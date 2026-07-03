// yahtzee.java
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class yahtzee {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[91m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String CYAN = "\u001B[96m";
    private static final String BOLD = "\u001B[1m";

    private static String colorize(String text, String color) {
        return color + text + RESET;
    }

    private static final String[] CATEGORIES = {
        "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
        "три одинаковых", "четыре одинаковых", "фулл-хаус",
        "малый стрит", "большой стрит", "шанс", "яхтзи"
    };
    private static final String[] DICE_EMOJI = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
    private static final String[] DICE_COLORS = {RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN};

    static class Player {
        String name;
        int score = 0;
        Map<String, Integer> categories = new HashMap<>();
    }

    static class Stats {
        int games = 0;
        Map<String, Integer> wins = new HashMap<>();
    }

    private String mode;
    private List<Player> players = new ArrayList<>();
    private int current;
    private int[] dice = new int[5];
    private int rollsLeft;
    private boolean[] keep = new boolean[5];
    private boolean gameOver;
    private String statsFile;
    private Stats stats;
    private Scanner scanner;

    public yahtzee(String mode, List<String> names) {
        this.mode = mode;
        if (names == null || names.isEmpty()) {
            names = Arrays.asList("Игрок 1", mode.equals("ai") ? "Компьютер" : "Игрок 2");
        }
        for (String n : names) {
            Player p = new Player();
            p.name = n;
            players.add(p);
        }
        current = 0;
        gameOver = false;
        statsFile = System.getProperty("user.home") + "/.yahtzee_stats.json";
        loadStats();
        scanner = new Scanner(System.in);
    }

    private void loadStats() {
        try {
            String json = new String(Files.readAllBytes(Paths.get(statsFile)));
            // упрощённый парсинг (без библиотеки)
            stats = new Stats();
            int gIdx = json.indexOf("\"games\"");
            if (gIdx != -1) {
                int start = json.indexOf(":", gIdx) + 1;
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                stats.games = Integer.parseInt(json.substring(start, end).trim());
            }
            // wins парсить не будем для простоты
        } catch (Exception e) {
            stats = new Stats();
        }
        if (stats == null) stats = new Stats();
    }

    private void saveStats() {
        try {
            String json = "{\"games\":" + stats.games + ",\"wins\":{";
            boolean first = true;
            for (Map.Entry<String, Integer> e : stats.wins.entrySet()) {
                if (!first) json += ",";
                first = false;
                json += "\"" + e.getKey() + "\":" + e.getValue();
            }
            json += "}}";
            Files.write(Paths.get(statsFile), json.getBytes());
        } catch (Exception e) {}
    }

    public void displayStats() {
        if (stats.games == 0) {
            System.out.println(colorize("Статистика пуста.", YELLOW));
            return;
        }
        System.out.println(colorize("📊 Статистика:", BOLD));
        System.out.println("  Всего игр: " + stats.games);
        for (Map.Entry<String, Integer> e : stats.wins.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue() + " побед");
        }
    }

    private void rollDice() {
        Random rand = new Random();
        for (int i=0; i<5; i++) {
            if (!keep[i]) dice[i] = rand.nextInt(6) + 1;
        }
        rollsLeft--;
    }

    private String getDiceDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<5; i++) {
            sb.append(colorize(DICE_EMOJI[dice[i]-1] + dice[i], DICE_COLORS[dice[i]-1])).append(" ");
        }
        return sb.toString().trim();
    }

    private void showDice() {
        System.out.println("Кости: " + getDiceDisplay());
    }

    private void showCategories(int playerIdx) {
        Player p = players.get(playerIdx);
        System.out.println("\nДоступные категории:");
        int num = 1;
        for (String cat : CATEGORIES) {
            if (!p.categories.containsKey(cat)) {
                System.out.println("  " + num + ". " + cat);
            }
            num++;
        }
    }

    private int scoreCategory(int[] dice, String cat) {
        int[] counts = new int[7];
        int total = 0;
        for (int d : dice) { counts[d]++; total += d; }
        switch (cat) {
            case "единицы": return counts[1] * 1;
            case "двойки": return counts[2] * 2;
            case "тройки": return counts[3] * 3;
            case "четвёрки": return counts[4] * 4;
            case "пятёрки": return counts[5] * 5;
            case "шестёрки": return counts[6] * 6;
            case "три одинаковых":
                for (int c : counts) if (c >= 3) return total;
                return 0;
            case "четыре одинаковых":
                for (int c : counts) if (c >= 4) return total;
                return 0;
            case "фулл-хаус":
                boolean has3 = false, has2 = false;
                for (int c : counts) {
                    if (c == 3) has3 = true;
                    if (c == 2) has2 = true;
                    if (c == 5) return 25;
                }
                if (has3 && has2) return 25;
                return 0;
            case "малый стрит":
                for (int start=1; start<=3; start++) {
                    boolean ok = true;
                    for (int i=0; i<4; i++) if (counts[start+i] == 0) { ok = false; break; }
                    if (ok) return 30;
                }
                return 0;
            case "большой стрит":
                boolean ok1 = true, ok2 = true;
                for (int i=1; i<=5; i++) if (counts[i] == 0) ok1 = false;
                for (int i=2; i<=6; i++) if (counts[i] == 0) ok2 = false;
                if (ok1 || ok2) return 40;
                return 0;
            case "шанс": return total;
            case "яхтзи":
                for (int c : counts) if (c == 5) return 50;
                return 0;
            default: return 0;
        }
    }

    private String[] bestCategory(int[] dice, int playerIdx) {
        int best = -1;
        String bestCat = null;
        Player p = players.get(playerIdx);
        for (String cat : CATEGORIES) {
            if (p.categories.containsKey(cat)) continue;
            int score = scoreCategory(dice, cat);
            if (score > best) {
                best = score;
                bestCat = cat;
            }
        }
        return new String[]{bestCat, String.valueOf(best)};
    }

    private void takeTurn(int playerIdx) {
        Player p = players.get(playerIdx);
        System.out.println("\n" + colorize("Ход: " + p.name, BOLD));
        rollsLeft = 3;
        keep = new boolean[5];
        rollDice();
        showDice();

        while (rollsLeft > 0) {
            if (mode.equals("ai") && playerIdx == 1) {
                int[] counts = new int[7];
                for (int d : dice) counts[d]++;
                int maxCnt = 0, target = 0;
                for (int i=1; i<=6; i++) {
                    if (counts[i] > maxCnt) {
                        maxCnt = counts[i];
                        target = i;
                    }
                }
                if (maxCnt >= 2) {
                    for (int i=0; i<5; i++) keep[i] = (dice[i] == target);
                } else {
                    keep = new boolean[5];
                }
                rollDice();
                showDice();
                break;
            } else {
                System.out.print("Введите номера кубиков для удержания (через пробел, 0 - оставить все): ");
                String line = scanner.nextLine().trim();
                if (line.equals("q")) System.exit(0);
                if (line.equals("0")) {
                    keep = new boolean[]{true, true, true, true, true};
                } else {
                    keep = new boolean[5];
                    for (String token : line.split("\\s+")) {
                        try {
                            int idx = Integer.parseInt(token) - 1;
                            if (idx >= 0 && idx < 5) keep[idx] = true;
                        } catch (NumberFormatException e) {}
                    }
                }
                if (rollsLeft > 1) {
                    System.out.println("Осталось бросков: " + (rollsLeft-1));
                    System.out.print("Бросить ещё? (y/n): ");
                    String ans = scanner.nextLine().trim().toLowerCase();
                    if (ans.equals("y")) {
                        rollDice();
                        showDice();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (mode.equals("ai") && playerIdx == 1) {
            String[] res = bestCategory(dice, playerIdx);
            String cat = res[0];
            int score = Integer.parseInt(res[1]);
            if (cat != null) {
                System.out.println("Компьютер выбрал категорию: " + cat + " (+" + score + " очков)");
                p.categories.put(cat, score);
                p.score += score;
            } else {
                System.out.println("Нет доступных категорий. Ход пропущен.");
            }
        } else {
            showCategories(playerIdx);
            while (true) {
                System.out.print("Выберите категорию (номер): ");
                String line = scanner.nextLine().trim();
                if (line.equals("q")) System.exit(0);
                try {
                    int idx = Integer.parseInt(line) - 1;
                    if (idx >= 0 && idx < CATEGORIES.length) {
                        String cat = CATEGORIES[idx];
                        if (p.categories.containsKey(cat)) {
                            System.out.println("Эта категория уже использована.");
                            continue;
                        }
                        int score = scoreCategory(dice, cat);
                        p.categories.put(cat, score);
                        p.score += score;
                        System.out.println("Категория '" + cat + "' записана! +" + score + " очков");
                        break;
                    } else {
                        System.out.println("Неверный номер.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Введите число.");
                }
            }
        }
    }

    public void play() {
        System.out.println(colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", BOLD));
        System.out.println("Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию.");
        System.out.println("У вас 3 броска за ход.\n");

        while (!gameOver) {
            Player p = players.get(current);
            if (p.categories.size() == CATEGORIES.length) {
                current = (current + 1) % players.size();
                boolean allDone = true;
                for (Player pl : players) {
                    if (pl.categories.size() < CATEGORIES.length) { allDone = false; break; }
                }
                if (allDone) {
                    gameOver = true;
                    continue;
                }
                continue;
            }
            takeTurn(current);
            current = (current + 1) % players.size();
        }

        System.out.println("\n" + colorize("🏆 ИГРА ЗАВЕРШЕНА!", BOLD));
        for (Player p : players) {
            System.out.println(p.name + ": " + p.score + " очков");
        }
        Player winner = players.get(0);
        for (Player p : players) {
            if (p.score > winner.score) winner = p;
        }
        System.out.println(colorize("Победил " + winner.name + "!", GREEN));
        stats.games++;
        stats.wins.put(winner.name, stats.wins.getOrDefault(winner.name, 0) + 1);
        saveStats();
        scanner.close();
    }

    public static void main(String[] args) {
        String mode = "ai";
        List<String> names = null;
        boolean showStats = false, reset = false;
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("ai") || args[i].equals("vs")) mode = args[i];
            else if (args[i].equals("-n") && i+1 < args.length) {
                names = Arrays.asList(args[++i].split(","));
            } else if (args[i].equals("-s")) showStats = true;
            else if (args[i].equals("-r")) reset = true;
            else if (args[i].equals("-h")) {
                System.out.println("Usage: java yahtzee [ai|vs] [-n names] [-s] [-r]");
                return;
            }
        }
        if (reset) {
            String f = System.getProperty("user.home") + "/.yahtzee_stats.json";
            try { Files.deleteIfExists(Paths.get(f)); } catch (Exception e) {}
            System.out.println("Статистика сброшена.");
            return;
        }
        yahtzee game = new yahtzee(mode, names);
        if (showStats) {
            game.displayStats();
            return;
        }
        game.play();
    }
}
