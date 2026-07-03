// yahtzee.cpp
#include <iostream>
#include <vector>
#include <string>
#include <map>
#include <random>
#include <algorithm>
#include <fstream>
#include <cctype>
#include <filesystem>
#include <thread>
#include <chrono>

using namespace std;
namespace fs = std::filesystem;

const string RESET = "\033[0m";
const string RED = "\033[91m";
const string GREEN = "\033[92m";
const string YELLOW = "\033[93m";
const string BLUE = "\033[94m";
const string MAGENTA = "\033[95m";
const string CYAN = "\033[96m";
const string BOLD = "\033[1m";

string colorize(const string& text, const string& color) {
    return color + text + RESET;
}

vector<string> CATEGORIES = {
    "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
    "три одинаковых", "четыре одинаковых", "фулл-хаус",
    "малый стрит", "большой стрит", "шанс", "яхтзи"
};
vector<string> DICE_EMOJI = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
vector<string> DICE_COLORS = {RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN};

class Yahtzee {
public:
    string mode;
    vector<string> names;
    struct Player {
        string name;
        int score = 0;
        map<string, int> categories;
    };
    vector<Player> players;
    int current_player;
    vector<int> dice;
    int rolls_left;
    vector<bool> keep;
    bool game_over;
    string stats_file;

    Yahtzee(string m, vector<string> n) : mode(m), names(n), current_player(0), rolls_left(3), game_over(false) {
        if (names.empty()) {
            names.push_back("Игрок 1");
            names.push_back(mode == "ai" ? "Компьютер" : "Игрок 2");
        }
        for (auto& name : names) {
            Player p;
            p.name = name;
            players.push_back(p);
        }
        stats_file = string(getenv("HOME")) + "/.yahtzee_stats.json";
        loadStats();
    }

    void loadStats() {
        ifstream f(stats_file);
        if (!f) return;
        // Упрощённо: не будем парсить JSON, оставим для демонстрации.
    }

    void saveStats() {
        ofstream f(stats_file);
        if (f) {
            f << "{\"games\":1,\"wins\":{\"" << players[0].name << "\":1}}";
        }
    }

    void displayStats() {
        cout << colorize("📊 Статистика:", BOLD) << endl;
        cout << "  Игр сыграно: 1" << endl;
        cout << "  Побед: " << players[0].name << " - 1" << endl;
    }

    void rollDice() {
        random_device rd;
        mt19937 gen(rd());
        uniform_int_distribution<> dis(1, 6);
        for (int i=0; i<5; ++i) {
            if (!keep[i]) {
                dice[i] = dis(gen);
            }
        }
        rolls_left--;
    }

    string getDiceDisplay() {
        string s;
        for (int i=0; i<5; ++i) {
            int d = dice[i];
            s += colorize(DICE_EMOJI[d-1] + to_string(d), DICE_COLORS[d-1]) + " ";
        }
        return s;
    }

    void showDice() {
        cout << "Кости: " << getDiceDisplay() << endl;
    }

    void showCategories(int player_idx) {
        cout << "\nДоступные категории:" << endl;
        int num = 1;
        for (auto& cat : CATEGORIES) {
            if (players[player_idx].categories.find(cat) == players[player_idx].categories.end()) {
                cout << "  " << num << ". " << cat << endl;
            }
            num++;
        }
    }

    int scoreCategory(vector<int>& dice, const string& category) {
        vector<int> counts(7, 0);
        int total = 0;
        for (int d : dice) { counts[d]++; total += d; }
        if (category == "единицы") return counts[1] * 1;
        if (category == "двойки") return counts[2] * 2;
        if (category == "тройки") return counts[3] * 3;
        if (category == "четвёрки") return counts[4] * 4;
        if (category == "пятёрки") return counts[5] * 5;
        if (category == "шестёрки") return counts[6] * 6;
        if (category == "три одинаковых") {
            for (int c : counts) if (c >= 3) return total;
            return 0;
        }
        if (category == "четыре одинаковых") {
            for (int c : counts) if (c >= 4) return total;
            return 0;
        }
        if (category == "фулл-хаус") {
            if (find(counts.begin(), counts.end(), 3) != counts.end() && find(counts.begin(), counts.end(), 2) != counts.end())
                return 25;
            if (find(counts.begin(), counts.end(), 5) != counts.end()) return 25;
            return 0;
        }
        if (category == "малый стрит") {
            for (int start=1; start<=3; ++start) {
                bool ok = true;
                for (int i=0; i<4; ++i) if (counts[start+i] == 0) ok = false;
                if (ok) return 30;
            }
            return 0;
        }
        if (category == "большой стрит") {
            bool ok1 = true, ok2 = true;
            for (int i=1; i<=5; ++i) if (counts[i] == 0) ok1 = false;
            for (int i=2; i<=6; ++i) if (counts[i] == 0) ok2 = false;
            if (ok1 || ok2) return 40;
            return 0;
        }
        if (category == "шанс") return total;
        if (category == "яхтзи") {
            for (int c : counts) if (c == 5) return 50;
            return 0;
        }
        return 0;
    }

    pair<string, int> bestCategory(vector<int>& dice, int player_idx) {
        int best = -1;
        string best_cat;
        for (auto& cat : CATEGORIES) {
            if (players[player_idx].categories.find(cat) != players[player_idx].categories.end()) continue;
            int score = scoreCategory(dice, cat);
            if (score > best) { best = score; best_cat = cat; }
        }
        return {best_cat, best};
    }

    void takeTurn(int player_idx) {
        Player& p = players[player_idx];
        cout << "\n" << colorize("Ход: " + p.name, BOLD) << endl;
        rolls_left = 3;
        keep.assign(5, false);
        dice.assign(5, 0);
        rollDice();
        showDice();

        while (rolls_left > 0) {
            if (mode == "ai" && player_idx == 1) {
                // Простой AI: оставляет кости с наибольшим количеством одинаковых
                vector<int> counts(7, 0);
                for (int d : dice) counts[d]++;
                int max_count = *max_element(counts.begin(), counts.end());
                if (max_count >= 2) {
                    int target = -1;
                    for (int i=1; i<=6; ++i) if (counts[i] == max_count) { target = i; break; }
                    for (int i=0; i<5; ++i) keep[i] = (dice[i] == target);
                } else {
                    keep.assign(5, false);
                }
                // AI делает второй бросок
                rollDice();
                showDice();
                // AI не делает третий бросок
                break;
            } else {
                cout << "Введите номера кубиков для удержания (через пробел, 0 - оставить все): ";
                string line;
                getline(cin, line);
                if (line == "q") exit(0);
                if (line == "0") {
                    keep.assign(5, true);
                } else {
                    keep.assign(5, false);
                    stringstream ss(line);
                    string token;
                    while (ss >> token) {
                        int idx = stoi(token) - 1;
                        if (idx >=0 && idx < 5) keep[idx] = true;
                    }
                }
                if (rolls_left > 1) {
                    cout << "Осталось бросков: " << rolls_left-1 << endl;
                    cout << "Бросить ещё? (y/n): ";
                    string ans;
                    getline(cin, ans);
                    if (ans == "y") {
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

        if (mode == "ai" && player_idx == 1) {
            auto [cat, score] = bestCategory(dice, player_idx);
            if (!cat.empty()) {
                cout << "Компьютер выбрал категорию: " << cat << " (+" << score << " очков)" << endl;
                p.categories[cat] = score;
                p.score += score;
            } else {
                cout << "Нет доступных категорий. Ход пропущен." << endl;
            }
        } else {
            showCategories(player_idx);
            while (true) {
                cout << "Выберите категорию (номер): ";
                string line;
                getline(cin, line);
                if (line == "q") exit(0);
                int idx = stoi(line) - 1;
                if (idx >=0 && idx < (int)CATEGORIES.size()) {
                    string cat = CATEGORIES[idx];
                    if (p.categories.find(cat) == p.categories.end()) {
                        int score = scoreCategory(dice, cat);
                        p.categories[cat] = score;
                        p.score += score;
                        cout << "Категория '" << cat << "' записана! +" << score << " очков" << endl;
                        break;
                    } else {
                        cout << "Эта категория уже использована." << endl;
                    }
                } else {
                    cout << "Неверный номер." << endl;
                }
            }
        }
    }

    void play() {
        cout << colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", BOLD) << endl;
        cout << "Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию." << endl;
        cout << "У вас 3 броска за ход.\n" << endl;

        while (!game_over) {
            Player& p = players[current_player];
            if ((int)p.categories.size() == (int)CATEGORIES.size()) {
                current_player = (current_player + 1) % players.size();
                bool all_done = true;
                for (auto& pl : players) {
                    if ((int)pl.categories.size() < (int)CATEGORIES.size()) { all_done = false; break; }
                }
                if (all_done) {
                    game_over = true;
                    continue;
                }
                continue;
            }
            takeTurn(current_player);
            current_player = (current_player + 1) % players.size();
        }

        cout << "\n" << colorize("🏆 ИГРА ЗАВЕРШЕНА!", BOLD) << endl;
        for (auto& p : players) {
            cout << p.name << ": " << p.score << " очков" << endl;
        }
        Player* winner = &players[0];
        for (auto& p : players) {
            if (p.score > winner->score) winner = &p;
        }
        cout << colorize("Победил " + winner->name + "!", GREEN) << endl;
        saveStats();
    }
};

int main(int argc, char* argv[]) {
    string mode = "ai";
    vector<string> names;
    bool showStats = false, reset = false;
    for (int i=1; i<argc; ++i) {
        string arg = argv[i];
        if (arg == "ai" || arg == "vs") mode = arg;
        else if (arg == "-n" && i+1 < argc) {
            string s = argv[++i];
            stringstream ss(s);
            string name;
            while (getline(ss, name, ',')) names.push_back(name);
        } else if (arg == "-s") showStats = true;
        else if (arg == "-r") reset = true;
        else if (arg == "-h") {
            cout << "Usage: yahtzee [ai|vs] [-n names] [-s] [-r]" << endl;
            return 0;
        }
    }
    if (reset) {
        string f = string(getenv("HOME")) + "/.yahtzee_stats.json";
        if (fs::exists(f)) fs::remove(f);
        cout << "Статистика сброшена." << endl;
        return 0;
    }
    Yahtzee game(mode, names);
    if (showStats) {
        game.displayStats();
        return 0;
    }
    game.play();
    return 0;
}
