# yahtzee.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import json
import random
from pathlib import Path

# ANSI-цвета
COLORS = {
    'reset': '\033[0m',
    'red': '\033[91m',
    'green': '\033[92m',
    'yellow': '\033[93m',
    'blue': '\033[94m',
    'magenta': '\033[95m',
    'cyan': '\033[96m',
    'bold': '\033[1m'
}

def colorize(text, color):
    return f"{COLORS.get(color, '')}{text}{COLORS['reset']}"

DICE_EMOJI = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅']

class Yahtzee:
    CATEGORIES = [
        "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
        "три одинаковых", "четыре одинаковых", "фулл-хаус",
        "малый стрит", "большой стрит", "шанс", "яхтзи"
    ]

    def __init__(self, mode='ai', names=None):
        self.mode = mode
        self.names = names or ["Игрок 1", "Игрок 2" if mode == 'vs' else "Компьютер"]
        self.players = []
        for name in self.names:
            self.players.append({'name': name, 'score': 0, 'categories': {}})
        self.current_player = 0
        self.dice = [0] * 5
        self.rolls_left = 3
        self.keep = [False] * 5
        self.game_over = False
        self.stats_file = Path.home() / '.yahtzee_stats.json'
        self.load_stats()

    def load_stats(self):
        if self.stats_file.exists():
            with open(self.stats_file, 'r') as f:
                self.stats = json.load(f)
        else:
            self.stats = {'games': 0, 'wins': {}}

    def save_stats(self):
        with open(self.stats_file, 'w') as f:
            json.dump(self.stats, f, indent=2)

    def roll_dice(self):
        for i in range(5):
            if not self.keep[i]:
                self.dice[i] = random.randint(1, 6)
        self.rolls_left -= 1

    def get_dice_display(self):
        return ' '.join(colorize(f"{DICE_EMOJI[d-1]}{d}", ['red','green','yellow','blue','magenta','cyan'][d-1]) for d in self.dice)

    def show_dice(self):
        print(f"Кости: {self.get_dice_display()}")

    def show_categories(self, player_idx):
        player = self.players[player_idx]
        print("\nДоступные категории:")
        for i, cat in enumerate(self.CATEGORIES):
            if cat not in player['categories']:
                print(f"  {i+1}. {cat}")

    def score_category(self, dice, category):
        counts = [0] * 7
        for d in dice:
            counts[d] += 1
        total = sum(dice)
        if category == "единицы": return counts[1] * 1
        elif category == "двойки": return counts[2] * 2
        elif category == "тройки": return counts[3] * 3
        elif category == "четвёрки": return counts[4] * 4
        elif category == "пятёрки": return counts[5] * 5
        elif category == "шестёрки": return counts[6] * 6
        elif category == "три одинаковых":
            if any(c >= 3 for c in counts): return total
            return 0
        elif category == "четыре одинаковых":
            if any(c >= 4 for c in counts): return total
            return 0
        elif category == "фулл-хаус":
            if (3 in counts and 2 in counts) or (5 in counts):
                return 25
            return 0
        elif category == "малый стрит":
            # Проверяем наличие 4 последовательных
            for start in range(1, 4):
                if all(counts[start+i] > 0 for i in range(4)):
                    return 30
            return 0
        elif category == "большой стрит":
            if all(counts[i] > 0 for i in range(1, 6)):
                return 40
            if all(counts[i] > 0 for i in range(2, 7)):
                return 40
            return 0
        elif category == "шанс":
            return total
        elif category == "яхтзи":
            if any(c == 5 for c in counts):
                return 50
            return 0
        return 0

    def best_category(self, dice):
        best_score = -1
        best_cat = None
        for cat in self.CATEGORIES:
            if cat in self.players[self.current_player]['categories']:
                continue
            score = self.score_category(dice, cat)
            if score > best_score:
                best_score = score
                best_cat = cat
        return best_cat, best_score

    def take_turn(self, player_idx):
        player = self.players[player_idx]
        print(f"\n{'='*40}")
        print(colorize(f"Ход: {player['name']}", 'bold'))

        self.rolls_left = 3
        self.keep = [False] * 5
        self.roll_dice()
        self.show_dice()

        while self.rolls_left > 0:
            if self.mode == 'ai' and player_idx == 1:
                # AI выбирает, какие кости оставить (простая стратегия: оставляет те, что дают лучшую комбинацию)
                # Для простоты AI не перебирает все варианты, а просто оставляет большинство одинаковых.
                counts = [0] * 7
                for d in self.dice:
                    counts[d] += 1
                max_count = max(counts)
                if max_count >= 2:
                    target = counts.index(max_count)
                    self.keep = [d == target for d in self.dice]
                else:
                    self.keep = [False] * 5
                # AI делает второй бросок
                self.roll_dice()
                self.show_dice()
                # AI не делает третий бросок для простоты
                break
            else:
                # Игрок выбирает кости для удержания
                choice = input("Введите номера кубиков для удержания (через пробел, 0 - оставить все): ").strip()
                if choice == 'q':
                    sys.exit(0)
                if choice == '0':
                    self.keep = [True] * 5
                else:
                    self.keep = [False] * 5
                    for idx in choice.split():
                        try:
                            i = int(idx) - 1
                            if 0 <= i < 5:
                                self.keep[i] = True
                        except:
                            pass
                if self.rolls_left > 1:
                    print(f"Осталось бросков: {self.rolls_left-1}")
                    choice = input("Бросить ещё? (y/n): ").strip().lower()
                    if choice == 'y':
                        self.roll_dice()
                        self.show_dice()
                    else:
                        break
                else:
                    break

        # Выбор категории
        if self.mode == 'ai' and player_idx == 1:
            cat, score = self.best_category(self.dice)
            if cat:
                print(f"Компьютер выбрал категорию: {cat} (+{score} очков)")
                player['categories'][cat] = score
                player['score'] += score
            else:
                print("Нет доступных категорий. Ход пропущен.")
        else:
            self.show_categories(player_idx)
            while True:
                choice = input("Выберите категорию (номер): ").strip()
                if choice == 'q':
                    sys.exit(0)
                try:
                    idx = int(choice) - 1
                    if 0 <= idx < len(self.CATEGORIES):
                        cat = self.CATEGORIES[idx]
                        if cat not in player['categories']:
                            score = self.score_category(self.dice, cat)
                            player['categories'][cat] = score
                            player['score'] += score
                            print(f"Категория '{cat}' записана! +{score} очков")
                            break
                        else:
                            print("Эта категория уже использована.")
                    else:
                        print("Неверный номер.")
                except:
                    print("Введите число.")

    def play(self):
        print(colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", 'bold'))
        print("Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию.")
        print("У вас 3 броска за ход.\n")

        while not self.game_over:
            player = self.players[self.current_player]
            # Проверяем, заполнены ли все категории
            if len(player['categories']) == len(self.CATEGORIES):
                self.current_player = (self.current_player + 1) % len(self.players)
                # Проверяем, все ли игроки завершили
                if all(len(p['categories']) == len(self.CATEGORIES) for p in self.players):
                    self.game_over = True
                continue
            self.take_turn(self.current_player)
            self.current_player = (self.current_player + 1) % len(self.players)

        # Итоги
        print("\n" + colorize("🏆 ИГРА ЗАВЕРШЕНА!", 'bold'))
        for p in self.players:
            print(f"{p['name']}: {p['score']} очков")
        winner = max(self.players, key=lambda p: p['score'])
        print(colorize(f"Победил {winner['name']}!", 'green'))
        # Обновление статистики
        self.stats['games'] += 1
        if winner['name'] not in self.stats['wins']:
            self.stats['wins'][winner['name']] = 0
        self.stats['wins'][winner['name']] += 1
        self.save_stats()

    def display_stats(self):
        if not self.stats or self.stats.get('games', 0) == 0:
            print(colorize("Статистика пуста.", 'yellow'))
            return
        print(colorize("📊 Статистика:", 'bold'))
        print(f"  Всего игр: {self.stats['games']}")
        for name, wins in self.stats.get('wins', {}).items():
            print(f"  {name}: {wins} побед")

def main():
    mode = 'ai'
    names = None
    show_stats = False
    reset = False
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        arg = args[i]
        if arg == 'ai' or arg == 'vs':
            mode = arg
        elif arg == '-n' and i+1 < len(args):
            names = [n.strip() for n in args[i+1].split(',')]
            i += 1
        elif arg == '-s' or arg == '--stats':
            show_stats = True
        elif arg == '-r' or arg == '--reset':
            reset = True
        elif arg == '-h' or arg == '--help':
            print("Usage: yahtzee.py [ai|vs] [-n names] [-s] [-r]")
            return
        i += 1
    if reset:
        stats_file = Path.home() / '.yahtzee_stats.json'
        if stats_file.exists():
            stats_file.unlink()
        print("Статистика сброшена.")
        return
    game = Yahtzee(mode, names)
    if show_stats:
        game.display_stats()
        return
    game.play()

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(colorize("\nИгра прервана.", 'yellow'))
        sys.exit(0)
