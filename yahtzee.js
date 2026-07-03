// yahtzee.js
#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');
const readline = require('readline');

const COLORS = {
    reset: '\x1b[0m',
    red: '\x1b[91m',
    green: '\x1b[92m',
    yellow: '\x1b[93m',
    blue: '\x1b[94m',
    magenta: '\x1b[95m',
    cyan: '\x1b[96m',
    bold: '\x1b[1m'
};

function colorize(text, color) {
    return COLORS[color] + text + COLORS.reset;
}

const CATEGORIES = [
    "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
    "три одинаковых", "четыре одинаковых", "фулл-хаус",
    "малый стрит", "большой стрит", "шанс", "яхтзи"
];
const DICE_EMOJI = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];
const DICE_COLORS = ['red', 'green', 'yellow', 'blue', 'magenta', 'cyan'];

class Yahtzee {
    constructor(mode, names) {
        this.mode = mode;
        this.names = names || (mode === 'ai' ? ['Игрок 1', 'Компьютер'] : ['Игрок 1', 'Игрок 2']);
        this.players = this.names.map(name => ({ name, score: 0, categories: {} }));
        this.current = 0;
        this.dice = [0,0,0,0,0];
        this.rollsLeft = 3;
        this.keep = [false,false,false,false,false];
        this.gameOver = false;
        this.statsFile = path.join(os.homedir(), '.yahtzee_stats.json');
        this.loadStats();
    }

    loadStats() {
        try {
            this.stats = JSON.parse(fs.readFileSync(this.statsFile, 'utf8'));
        } catch {
            this.stats = { games: 0, wins: {} };
        }
    }

    saveStats() {
        fs.writeFileSync(this.statsFile, JSON.stringify(this.stats, null, 2));
    }

    displayStats() {
        if (this.stats.games === 0) {
            console.log(colorize('Статистика пуста.', 'yellow'));
            return;
        }
        console.log(colorize('📊 Статистика:', 'bold'));
        console.log(`  Всего игр: ${this.stats.games}`);
        for (const [name, wins] of Object.entries(this.stats.wins)) {
            console.log(`  ${name}: ${wins} побед`);
        }
    }

    rollDice() {
        for (let i=0; i<5; i++) {
            if (!this.keep[i]) {
                this.dice[i] = Math.floor(Math.random() * 6) + 1;
            }
        }
        this.rollsLeft--;
    }

    getDiceDisplay() {
        return this.dice.map((d, i) => colorize(DICE_EMOJI[d-1] + d, DICE_COLORS[d-1])).join(' ');
    }

    showDice() {
        console.log(`Кости: ${this.getDiceDisplay()}`);
    }

    showCategories(playerIdx) {
        const used = this.players[playerIdx].categories;
        console.log('\nДоступные категории:');
        let num = 1;
        for (const cat of CATEGORIES) {
            if (!used[cat]) {
                console.log(`  ${num}. ${cat}`);
            }
            num++;
        }
    }

    scoreCategory(dice, cat) {
        const counts = Array(7).fill(0);
        let total = 0;
        for (const d of dice) {
            counts[d]++;
            total += d;
        }
        switch (cat) {
            case 'единицы': return counts[1] * 1;
            case 'двойки': return counts[2] * 2;
            case 'тройки': return counts[3] * 3;
            case 'четвёрки': return counts[4] * 4;
            case 'пятёрки': return counts[5] * 5;
            case 'шестёрки': return counts[6] * 6;
            case 'три одинаковых':
                if (counts.some(c => c >= 3)) return total;
                return 0;
            case 'четыре одинаковых':
                if (counts.some(c => c >= 4)) return total;
                return 0;
            case 'фулл-хаус':
                if (counts.some(c => c === 3) && counts.some(c => c === 2)) return 25;
                if (counts.some(c => c === 5)) return 25;
                return 0;
            case 'малый стрит':
                for (let start=1; start<=3; start++) {
                    let ok = true;
                    for (let i=0; i<4; i++) {
                        if (counts[start+i] === 0) { ok = false; break; }
                    }
                    if (ok) return 30;
                }
                return 0;
            case 'большой стрит':
                let ok1 = true, ok2 = true;
                for (let i=1; i<=5; i++) if (counts[i] === 0) ok1 = false;
                for (let i=2; i<=6; i++) if (counts[i] === 0) ok2 = false;
                if (ok1 || ok2) return 40;
                return 0;
            case 'шанс': return total;
            case 'яхтзи':
                if (counts.some(c => c === 5)) return 50;
                return 0;
            default: return 0;
        }
    }

    bestCategory(dice, playerIdx) {
        let best = -1, bestCat = null;
        const used = this.players[playerIdx].categories;
        for (const cat of CATEGORIES) {
            if (used[cat]) continue;
            const score = this.scoreCategory(dice, cat);
            if (score > best) {
                best = score;
                bestCat = cat;
            }
        }
        return { cat: bestCat, score: best };
    }

    async takeTurn(playerIdx, rl) {
        const p = this.players[playerIdx];
        console.log(`\n${colorize('Ход: ' + p.name, 'bold')}`);
        this.rollsLeft = 3;
        this.keep = [false,false,false,false,false];
        this.rollDice();
        this.showDice();

        while (this.rollsLeft > 0) {
            if (this.mode === 'ai' && playerIdx === 1) {
                // Простой AI
                const counts = Array(7).fill(0);
                for (const d of this.dice) counts[d]++;
                let maxCnt = 0, target = 0;
                for (let i=1; i<=6; i++) {
                    if (counts[i] > maxCnt) {
                        maxCnt = counts[i];
                        target = i;
                    }
                }
                if (maxCnt >= 2) {
                    for (let i=0; i<5; i++) {
                        this.keep[i] = (this.dice[i] === target);
                    }
                } else {
                    this.keep = [false,false,false,false,false];
                }
                this.rollDice();
                this.showDice();
                break;
            } else {
                const answer = await new Promise(resolve => {
                    rl.question('Введите номера кубиков для удержания (через пробел, 0 - оставить все): ', resolve);
                });
                const line = answer.trim();
                if (line === 'q') process.exit(0);
                if (line === '0') {
                    this.keep = [true,true,true,true,true];
                } else {
                    this.keep = [false,false,false,false,false];
                    for (const token of line.split(/\s+/)) {
                        const idx = parseInt(token) - 1;
                        if (idx >= 0 && idx < 5) this.keep[idx] = true;
                    }
                }
                if (this.rollsLeft > 1) {
                    console.log(`Осталось бросков: ${this.rollsLeft-1}`);
                    const ans = await new Promise(resolve => {
                        rl.question('Бросить ещё? (y/n): ', resolve);
                    });
                    if (ans.trim().toLowerCase() === 'y') {
                        this.rollDice();
                        this.showDice();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (this.mode === 'ai' && playerIdx === 1) {
            const { cat, score } = this.bestCategory(this.dice, playerIdx);
            if (cat) {
                console.log(`Компьютер выбрал категорию: ${cat} (+${score} очков)`);
                p.categories[cat] = score;
                p.score += score;
            } else {
                console.log('Нет доступных категорий. Ход пропущен.');
            }
        } else {
            this.showCategories(playerIdx);
            while (true) {
                const ans = await new Promise(resolve => {
                    rl.question('Выберите категорию (номер): ', resolve);
                });
                if (ans === 'q') process.exit(0);
                const idx = parseInt(ans) - 1;
                if (idx >= 0 && idx < CATEGORIES.length) {
                    const cat = CATEGORIES[idx];
                    if (p.categories[cat]) {
                        console.log('Эта категория уже использована.');
                        continue;
                    }
                    const score = this.scoreCategory(this.dice, cat);
                    p.categories[cat] = score;
                    p.score += score;
                    console.log(`Категория '${cat}' записана! +${score} очков`);
                    break;
                } else {
                    console.log('Неверный номер.');
                }
            }
        }
    }

    async play() {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        console.log(colorize('🎲 Добро пожаловать в Яхтзи (покер на костях)!', 'bold'));
        console.log('Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию.');
        console.log('У вас 3 броска за ход.\n');

        while (!this.gameOver) {
            const p = this.players[this.current];
            if (Object.keys(p.categories).length === CATEGORIES.length) {
                this.current = (this.current + 1) % this.players.length;
                if (this.players.every(p => Object.keys(p.categories).length === CATEGORIES.length)) {
                    this.gameOver = true;
                    continue;
                }
                continue;
            }
            await this.takeTurn(this.current, rl);
            this.current = (this.current + 1) % this.players.length;
        }

        console.log('\n' + colorize('🏆 ИГРА ЗАВЕРШЕНА!', 'bold'));
        for (const p of this.players) {
            console.log(`${p.name}: ${p.score} очков`);
        }
        const winner = this.players.reduce((a, b) => a.score > b.score ? a : b);
        console.log(colorize(`Победил ${winner.name}!`, 'green'));
        this.stats.games++;
        if (!this.stats.wins[winner.name]) this.stats.wins[winner.name] = 0;
        this.stats.wins[winner.name]++;
        this.saveStats();
        rl.close();
    }
}

async function main() {
    const args = process.argv.slice(2);
    let mode = 'ai';
    let names = [];
    let showStats = false;
    let reset = false;
    for (let i=0; i<args.length; i++) {
        const arg = args[i];
        if (arg === 'ai' || arg === 'vs') mode = arg;
        else if (arg === '-n' && i+1 < args.length) {
            names = args[++i].split(',').map(s => s.trim());
        } else if (arg === '-s') showStats = true;
        else if (arg === '-r') reset = true;
        else if (arg === '-h') {
            console.log('Usage: node yahtzee.js [ai|vs] [-n names] [-s] [-r]');
            process.exit(0);
        }
    }
    if (reset) {
        const f = path.join(os.homedir(), '.yahtzee_stats.json');
        if (fs.existsSync(f)) fs.unlinkSync(f);
        console.log('Статистика сброшена.');
        return;
    }
    const game = new Yahtzee(mode, names);
    if (showStats) {
        game.displayStats();
        return;
    }
    await game.play();
}

main().catch(console.error);
