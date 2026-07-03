// yahtzee.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;

class YahtzeeGame
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "red" => "\x1b[91m",
            "green" => "\x1b[92m",
            "yellow" => "\x1b[93m",
            "blue" => "\x1b[94m",
            "magenta" => "\x1b[95m",
            "cyan" => "\x1b[96m",
            "bold" => "\x1b[1m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    static string[] CATEGORIES = {
        "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
        "три одинаковых", "четыре одинаковых", "фулл-хаус",
        "малый стрит", "большой стрит", "шанс", "яхтзи"
    };
    static string[] DICE_EMOJI = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
    static string[] DICE_COLORS = {"red", "green", "yellow", "blue", "magenta", "cyan"};

    class Player
    {
        public string Name { get; set; }
        public int Score { get; set; }
        public Dictionary<string, int> Categories { get; set; } = new Dictionary<string, int>();
    }

    class Stats
    {
        public int games { get; set; }
        public Dictionary<string, int> wins { get; set; } = new Dictionary<string, int>();
    }

    private string mode;
    private List<Player> players = new List<Player>();
    private int current;
    private int[] dice = new int[5];
    private int rollsLeft;
    private bool[] keep = new bool[5];
    private bool gameOver;
    private string statsFile;
    private Stats stats;

    public YahtzeeGame(string mode, List<string> names)
    {
        this.mode = mode;
        if (names == null || names.Count == 0)
        {
            names = new List<string> { "Игрок 1", mode == "ai" ? "Компьютер" : "Игрок 2" };
        }
        foreach (var n in names) players.Add(new Player { Name = n });
        current = 0;
        gameOver = false;
        statsFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".yahtzee_stats.json");
        LoadStats();
    }

    void LoadStats()
    {
        if (File.Exists(statsFile))
        {
            try
            {
                string json = File.ReadAllText(statsFile);
                stats = JsonSerializer.Deserialize<Stats>(json);
            }
            catch { }
        }
        if (stats == null) stats = new Stats();
    }

    void SaveStats()
    {
        string json = JsonSerializer.Serialize(stats);
        File.WriteAllText(statsFile, json);
    }

    public void DisplayStats()
    {
        if (stats.games == 0)
        {
            Console.WriteLine(Colorize("Статистика пуста.", "yellow"));
            return;
        }
        Console.WriteLine(Colorize("📊 Статистика:", "bold"));
        Console.WriteLine($"  Всего игр: {stats.games}");
        foreach (var kv in stats.wins)
            Console.WriteLine($"  {kv.Key}: {kv.Value} побед");
    }

    void RollDice()
    {
        Random rnd = new Random();
        for (int i=0; i<5; i++)
            if (!keep[i]) dice[i] = rnd.Next(1, 7);
        rollsLeft--;
    }

    string GetDiceDisplay()
    {
        return string.Join(" ", dice.Select((d, i) => Colorize(DICE_EMOJI[d-1] + d.ToString(), DICE_COLORS[d-1])));
    }

    void ShowDice() => Console.WriteLine($"Кости: {GetDiceDisplay()}");

    void ShowCategories(int playerIdx)
    {
        var used = players[playerIdx].Categories;
        Console.WriteLine("\nДоступные категории:");
        int num = 1;
        foreach (var cat in CATEGORIES)
        {
            if (!used.ContainsKey(cat))
                Console.WriteLine($"  {num}. {cat}");
            num++;
        }
    }

    int ScoreCategory(int[] dice, string cat)
    {
        int[] counts = new int[7];
        int total = 0;
        foreach (int d in dice) { counts[d]++; total += d; }
        switch (cat)
        {
            case "единицы": return counts[1] * 1;
            case "двойки": return counts[2] * 2;
            case "тройки": return counts[3] * 3;
            case "четвёрки": return counts[4] * 4;
            case "пятёрки": return counts[5] * 5;
            case "шестёрки": return counts[6] * 6;
            case "три одинаковых":
                if (counts.Any(c => c >= 3)) return total;
                return 0;
            case "четыре одинаковых":
                if (counts.Any(c => c >= 4)) return total;
                return 0;
            case "фулл-хаус":
                if (counts.Any(c => c == 3) && counts.Any(c => c == 2)) return 25;
                if (counts.Any(c => c == 5)) return 25;
                return 0;
            case "малый стрит":
                for (int start=1; start<=3; start++)
                {
                    bool ok = true;
                    for (int i=0; i<4; i++)
                        if (counts[start+i] == 0) { ok = false; break; }
                    if (ok) return 30;
                }
                return 0;
            case "большой стрит":
                bool ok1 = true, ok2 = true;
                for (int i=1; i<=5; i++) if (counts[i] == 0) ok1 = false;
                for (int i=2; i<=6; i++) if (counts[i] == 0) ok2 = false;
                if (ok1 || ok2) return 40;
                return 0;
            case "шанс": return total;
            case "яхтзи":
                if (counts.Any(c => c == 5)) return 50;
                return 0;
            default: return 0;
        }
    }

    (string cat, int score) BestCategory(int[] dice, int playerIdx)
    {
        int best = -1;
        string bestCat = null;
        var used = players[playerIdx].Categories;
        foreach (var cat in CATEGORIES)
        {
            if (used.ContainsKey(cat)) continue;
            int score = ScoreCategory(dice, cat);
            if (score > best) { best = score; bestCat = cat; }
        }
        return (bestCat, best);
    }

    void TakeTurn(int playerIdx)
    {
        var p = players[playerIdx];
        Console.WriteLine($"\n{Colorize("Ход: " + p.Name, "bold")}");
        rollsLeft = 3;
        keep = new bool[5];
        RollDice();
        ShowDice();

        while (rollsLeft > 0)
        {
            if (mode == "ai" && playerIdx == 1)
            {
                // Простой AI
                int[] counts = new int[7];
                foreach (int d in dice) counts[d]++;
                int maxCnt = counts.Max();
                int target = Array.IndexOf(counts, maxCnt);
                if (maxCnt >= 2)
                {
                    for (int i=0; i<5; i++) keep[i] = (dice[i] == target);
                }
                else keep = new bool[5];
                RollDice();
                ShowDice();
                break;
            }
            else
            {
                Console.Write("Введите номера кубиков для удержания (через пробел, 0 - оставить все): ");
                string line = Console.ReadLine().Trim();
                if (line == "q") Environment.Exit(0);
                if (line == "0") keep = new bool[] { true, true, true, true, true };
                else
                {
                    keep = new bool[5];
                    foreach (string token in line.Split(' '))
                    {
                        if (int.TryParse(token, out int idx) && idx >= 1 && idx <= 5)
                            keep[idx-1] = true;
                    }
                }
                if (rollsLeft > 1)
                {
                    Console.WriteLine($"Осталось бросков: {rollsLeft-1}");
                    Console.Write("Бросить ещё? (y/n): ");
                    string ans = Console.ReadLine().Trim().ToLower();
                    if (ans == "y") { RollDice(); ShowDice(); }
                    else break;
                }
                else break;
            }
        }

        if (mode == "ai" && playerIdx == 1)
        {
            var (cat, score) = BestCategory(dice, playerIdx);
            if (cat != null)
            {
                Console.WriteLine($"Компьютер выбрал категорию: {cat} (+{score} очков)");
                p.Categories[cat] = score;
                p.Score += score;
            }
            else Console.WriteLine("Нет доступных категорий. Ход пропущен.");
        }
        else
        {
            ShowCategories(playerIdx);
            while (true)
            {
                Console.Write("Выберите категорию (номер): ");
                string line = Console.ReadLine().Trim();
                if (line == "q") Environment.Exit(0);
                if (int.TryParse(line, out int idx) && idx >= 1 && idx <= CATEGORIES.Length)
                {
                    string cat = CATEGORIES[idx-1];
                    if (p.Categories.ContainsKey(cat))
                    {
                        Console.WriteLine("Эта категория уже использована.");
                        continue;
                    }
                    int score = ScoreCategory(dice, cat);
                    p.Categories[cat] = score;
                    p.Score += score;
                    Console.WriteLine($"Категория '{cat}' записана! +{score} очков");
                    break;
                }
                else Console.WriteLine("Неверный номер.");
            }
        }
    }

    public void Play()
    {
        Console.WriteLine(Colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", "bold"));
        Console.WriteLine("Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию.");
        Console.WriteLine("У вас 3 броска за ход.\n");

        while (!gameOver)
        {
            var p = players[current];
            if (p.Categories.Count == CATEGORIES.Length)
            {
                current = (current + 1) % players.Count;
                if (players.All(pl => pl.Categories.Count == CATEGORIES.Length))
                {
                    gameOver = true;
                    continue;
                }
                continue;
            }
            TakeTurn(current);
            current = (current + 1) % players.Count;
        }

        Console.WriteLine("\n" + Colorize("🏆 ИГРА ЗАВЕРШЕНА!", "bold"));
        foreach (var p in players)
            Console.WriteLine($"{p.Name}: {p.Score} очков");
        var winner = players.OrderByDescending(p => p.Score).First();
        Console.WriteLine(Colorize($"Победил {winner.Name}!", "green"));
        stats.games++;
        if (!stats.wins.ContainsKey(winner.Name)) stats.wins[winner.Name] = 0;
        stats.wins[winner.Name]++;
        SaveStats();
    }

    static void Main(string[] args)
    {
        string mode = "ai";
        List<string> names = null;
        bool showStats = false, reset = false;
        for (int i=0; i<args.Length; i++)
        {
            string arg = args[i];
            if (arg == "ai" || arg == "vs") mode = arg;
            else if (arg == "-n" && i+1 < args.Length)
                names = args[++i].Split(',').Select(s => s.Trim()).ToList();
            else if (arg == "-s") showStats = true;
            else if (arg == "-r") reset = true;
            else if (arg == "-h")
            {
                Console.WriteLine("Usage: yahtzee [ai|vs] [-n names] [-s] [-r]");
                return;
            }
        }
        if (reset)
        {
            string f = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".yahtzee_stats.json");
            if (File.Exists(f)) File.Delete(f);
            Console.WriteLine("Статистика сброшена.");
            return;
        }
        var game = new YahtzeeGame(mode, names);
        if (showStats)
        {
            game.DisplayStats();
            return;
        }
        game.Play();
    }
}
