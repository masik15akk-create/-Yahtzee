// yahtzee.go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const (
	reset  = "\033[0m"
	red    = "\033[91m"
	green  = "\033[92m"
	yellow = "\033[93m"
	blue   = "\033[94m"
	magenta= "\033[95m"
	cyan   = "\033[96m"
	bold   = "\033[1m"
)

func colorize(text, color string) string {
	return color + text + reset
}

var categories = []string{
	"единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
	"три одинаковых", "четыре одинаковых", "фулл-хаус",
	"малый стрит", "большой стрит", "шанс", "яхтзи",
}
var diceEmoji = []string{"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"}
var diceColors = []string{red, green, yellow, blue, magenta, cyan}

type Player struct {
	Name       string         `json:"name"`
	Score      int            `json:"score"`
	Categories map[string]int `json:"categories"`
}

type Yahtzee struct {
	Mode         string
	Players      []Player
	Current      int
	Dice         []int
	RollsLeft    int
	Keep         []bool
	GameOver     bool
	StatsFile    string
	Stats        map[string]interface{}
}

func NewYahtzee(mode string, names []string) *Yahtzee {
	if len(names) == 0 {
		names = []string{"Игрок 1", "Игрок 2"}
		if mode == "ai" {
			names[1] = "Компьютер"
		}
	}
	y := &Yahtzee{
		Mode:      mode,
		Players:   []Player{},
		Current:   0,
		Dice:      make([]int, 5),
		RollsLeft: 3,
		Keep:      make([]bool, 5),
		GameOver:  false,
	}
	for _, n := range names {
		y.Players = append(y.Players, Player{Name: n, Score: 0, Categories: make(map[string]int)})
	}
	y.StatsFile = filepath.Join(os.Getenv("HOME"), ".yahtzee_stats.json")
	y.loadStats()
	return y
}

func (y *Yahtzee) loadStats() {
	data, err := os.ReadFile(y.StatsFile)
	if err == nil {
		json.Unmarshal(data, &y.Stats)
	}
	if y.Stats == nil {
		y.Stats = map[string]interface{}{"games": 0, "wins": map[string]int{}}
	}
}

func (y *Yahtzee) saveStats() {
	data, _ := json.MarshalIndent(y.Stats, "", "  ")
	os.WriteFile(y.StatsFile, data, 0644)
}

func (y *Yahtzee) displayStats() {
	if y.Stats["games"].(int) == 0 {
		fmt.Println(colorize("Статистика пуста.", yellow))
		return
	}
	fmt.Println(colorize("📊 Статистика:", bold))
	fmt.Printf("  Всего игр: %d\n", y.Stats["games"])
	wins := y.Stats["wins"].(map[string]int)
	for name, w := range wins {
		fmt.Printf("  %s: %d побед\n", name, w)
	}
}

func (y *Yahtzee) rollDice() {
	rand.Seed(time.Now().UnixNano())
	for i := 0; i < 5; i++ {
		if !y.Keep[i] {
			y.Dice[i] = rand.Intn(6) + 1
		}
	}
	y.RollsLeft--
}

func (y *Yahtzee) getDiceDisplay() string {
	var s []string
	for i, d := range y.Dice {
		s = append(s, colorize(diceEmoji[d-1]+strconv.Itoa(d), diceColors[d-1]))
	}
	return strings.Join(s, " ")
}

func (y *Yahtzee) showDice() {
	fmt.Printf("Кости: %s\n", y.getDiceDisplay())
}

func (y *Yahtzee) showCategories(playerIdx int) {
	fmt.Println("\nДоступные категории:")
	catMap := y.Players[playerIdx].Categories
	num := 1
	for _, cat := range categories {
		if _, ok := catMap[cat]; !ok {
			fmt.Printf("  %d. %s\n", num, cat)
		}
		num++
	}
}

func (y *Yahtzee) scoreCategory(dice []int, cat string) int {
	counts := make([]int, 7)
	total := 0
	for _, d := range dice {
		counts[d]++
		total += d
	}
	switch cat {
	case "единицы": return counts[1] * 1
	case "двойки": return counts[2] * 2
	case "тройки": return counts[3] * 3
	case "четвёрки": return counts[4] * 4
	case "пятёрки": return counts[5] * 5
	case "шестёрки": return counts[6] * 6
	case "три одинаковых":
		for _, c := range counts {
			if c >= 3 {
				return total
			}
		}
		return 0
	case "четыре одинаковых":
		for _, c := range counts {
			if c >= 4 {
				return total
			}
		}
		return 0
	case "фулл-хаус":
		has3 := false
		has2 := false
		for _, c := range counts {
			if c == 3 {
				has3 = true
			}
			if c == 2 {
				has2 = true
			}
			if c == 5 {
				return 25
			}
		}
		if has3 && has2 {
			return 25
		}
		return 0
	case "малый стрит":
		for start := 1; start <= 3; start++ {
			ok := true
			for i := 0; i < 4; i++ {
				if counts[start+i] == 0 {
					ok = false
					break
				}
			}
			if ok {
				return 30
			}
		}
		return 0
	case "большой стрит":
		ok1, ok2 := true, true
		for i := 1; i <= 5; i++ {
			if counts[i] == 0 {
				ok1 = false
			}
		}
		for i := 2; i <= 6; i++ {
			if counts[i] == 0 {
				ok2 = false
			}
		}
		if ok1 || ok2 {
			return 40
		}
		return 0
	case "шанс":
		return total
	case "яхтзи":
		for _, c := range counts {
			if c == 5 {
				return 50
			}
		}
		return 0
	}
	return 0
}

func (y *Yahtzee) bestCategory(dice []int, playerIdx int) (string, int) {
	best := -1
	bestCat := ""
	used := y.Players[playerIdx].Categories
	for _, cat := range categories {
		if _, ok := used[cat]; ok {
			continue
		}
		score := y.scoreCategory(dice, cat)
		if score > best {
			best = score
			bestCat = cat
		}
	}
	return bestCat, best
}

func (y *Yahtzee) takeTurn(playerIdx int) {
	p := &y.Players[playerIdx]
	fmt.Printf("\n%s\n", colorize("Ход: "+p.Name, bold))
	y.RollsLeft = 3
	y.Keep = make([]bool, 5)
	y.rollDice()
	y.showDice()

	scanner := bufio.NewScanner(os.Stdin)
	for y.RollsLeft > 0 {
		if y.Mode == "ai" && playerIdx == 1 {
			// Простой AI
			counts := make([]int, 7)
			for _, d := range y.Dice {
				counts[d]++
			}
			maxCnt := 0
			target := 0
			for i := 1; i <= 6; i++ {
				if counts[i] > maxCnt {
					maxCnt = counts[i]
					target = i
				}
			}
			if maxCnt >= 2 {
				for i := 0; i < 5; i++ {
					y.Keep[i] = (y.Dice[i] == target)
				}
			} else {
				y.Keep = make([]bool, 5)
			}
			y.rollDice()
			y.showDice()
			break
		} else {
			fmt.Print("Введите номера кубиков для удержания (через пробел, 0 - оставить все): ")
			scanner.Scan()
			line := scanner.Text()
			if line == "q" {
				os.Exit(0)
			}
			if line == "0" {
				y.Keep = make([]bool, 5)
				for i := range y.Keep {
					y.Keep[i] = true
				}
			} else {
				y.Keep = make([]bool, 5)
				parts := strings.Fields(line)
				for _, p := range parts {
					idx, _ := strconv.Atoi(p)
					if idx >= 1 && idx <= 5 {
						y.Keep[idx-1] = true
					}
				}
			}
			if y.RollsLeft > 1 {
				fmt.Printf("Осталось бросков: %d\n", y.RollsLeft-1)
				fmt.Print("Бросить ещё? (y/n): ")
				scanner.Scan()
				ans := scanner.Text()
				if ans == "y" {
					y.rollDice()
					y.showDice()
				} else {
					break
				}
			} else {
				break
			}
		}
	}

	if y.Mode == "ai" && playerIdx == 1 {
		cat, score := y.bestCategory(y.Dice, playerIdx)
		if cat != "" {
			fmt.Printf("Компьютер выбрал категорию: %s (+%d очков)\n", cat, score)
			p.Categories[cat] = score
			p.Score += score
		} else {
			fmt.Println("Нет доступных категорий. Ход пропущен.")
		}
	} else {
		y.showCategories(playerIdx)
		for {
			fmt.Print("Выберите категорию (номер): ")
			scanner.Scan()
			line := scanner.Text()
			if line == "q" {
				os.Exit(0)
			}
			idx, err := strconv.Atoi(line)
			if err != nil || idx < 1 || idx > len(categories) {
				fmt.Println("Неверный номер.")
				continue
			}
			cat := categories[idx-1]
			if _, ok := p.Categories[cat]; ok {
				fmt.Println("Эта категория уже использована.")
				continue
			}
			score := y.scoreCategory(y.Dice, cat)
			p.Categories[cat] = score
			p.Score += score
			fmt.Printf("Категория '%s' записана! +%d очков\n", cat, score)
			break
		}
	}
}

func (y *Yahtzee) play() {
	fmt.Println(colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", bold))
	fmt.Println("Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию.")
	fmt.Println("У вас 3 броска за ход.\n")

	for !y.GameOver {
		p := &y.Players[y.Current]
		if len(p.Categories) == len(categories) {
			y.Current = (y.Current + 1) % len(y.Players)
			allDone := true
			for _, pl := range y.Players {
				if len(pl.Categories) < len(categories) {
					allDone = false
					break
				}
			}
			if allDone {
				y.GameOver = true
				continue
			}
			continue
		}
		y.takeTurn(y.Current)
		y.Current = (y.Current + 1) % len(y.Players)
	}

	fmt.Println("\n" + colorize("🏆 ИГРА ЗАВЕРШЕНА!", bold))
	for _, p := range y.Players {
		fmt.Printf("%s: %d очков\n", p.Name, p.Score)
	}
	winner := &y.Players[0]
	for i := 1; i < len(y.Players); i++ {
		if y.Players[i].Score > winner.Score {
			winner = &y.Players[i]
		}
	}
	fmt.Println(colorize("Победил "+winner.Name+"!", green))
	y.Stats["games"] = y.Stats["games"].(int) + 1
	wins := y.Stats["wins"].(map[string]int)
	wins[winner.Name] = wins[winner.Name] + 1
	y.saveStats()
}

func main() {
	mode := "ai"
	names := []string{}
	showStats := false
	reset := false
	for i := 1; i < len(os.Args); i++ {
		arg := os.Args[i]
		switch arg {
		case "ai", "vs":
			mode = arg
		case "-n":
			if i+1 < len(os.Args) {
				names = strings.Split(os.Args[i+1], ",")
				i++
			}
		case "-s":
			showStats = true
		case "-r":
			reset = true
		case "-h":
			fmt.Println("Usage: yahtzee [ai|vs] [-n names] [-s] [-r]")
			return
		}
	}
	if reset {
		f := filepath.Join(os.Getenv("HOME"), ".yahtzee_stats.json")
		os.Remove(f)
		fmt.Println("Статистика сброшена.")
		return
	}
	game := NewYahtzee(mode, names)
	if showStats {
		game.displayStats()
		return
	}
	game.play()
}
