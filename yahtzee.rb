#!/usr/bin/env ruby
# yahtzee.rb
# encoding: UTF-8

require 'json'
require 'fileutils'

COLORS = {
  reset: "\e[0m",
  red: "\e[91m",
  green: "\e[92m",
  yellow: "\e[93m",
  blue: "\e[94m",
  magenta: "\e[95m",
  cyan: "\e[96m",
  bold: "\e[1m"
}

def colorize(text, color)
  "#{COLORS[color]}#{text}#{COLORS[:reset]}"
end

CATEGORIES = [
  "единицы", "двойки", "тройки", "четвёрки", "пятёрки", "шестёрки",
  "три одинаковых", "четыре одинаковых", "фулл-хаус",
  "малый стрит", "большой стрит", "шанс", "яхтзи"
]
DICE_EMOJI = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅']
DICE_COLORS = [:red, :green, :yellow, :blue, :magenta, :cyan]

class Yahtzee
  attr_reader :mode, :players, :current, :dice, :rolls_left, :keep, :game_over, :stats_file

  def initialize(mode, names)
    @mode = mode
    @players = (names || (mode == 'ai' ? ['Игрок 1', 'Компьютер'] : ['Игрок 1', 'Игрок 2'])).map do |n|
      { name: n, score: 0, categories: {} }
    end
    @current = 0
    @dice = [0,0,0,0,0]
    @rolls_left = 3
    @keep = [false,false,false,false,false]
    @game_over = false
    @stats_file = File.join(Dir.home, '.yahtzee_stats.json')
    load_stats
  end

  def load_stats
    if File.exist?(@stats_file)
      @stats = JSON.parse(File.read(@stats_file))
    else
      @stats = { 'games' => 0, 'wins' => {} }
    end
  end

  def save_stats
    File.write(@stats_file, JSON.pretty_generate(@stats))
  end

  def display_stats
    if @stats['games'] == 0
      puts colorize("Статистика пуста.", :yellow)
      return
    end
    puts colorize("📊 Статистика:", :bold)
    puts "  Всего игр: #{@stats['games']}"
    @stats['wins'].each { |name, wins| puts "  #{name}: #{wins} побед" }
  end

  def roll_dice
    5.times { |i| @dice[i] = rand(1..6) unless @keep[i] }
    @rolls_left -= 1
  end

  def dice_display
    @dice.each_with_index.map { |d, i| colorize(DICE_EMOJI[d-1] + d.to_s, DICE_COLORS[d-1]) }.join(' ')
  end

  def show_dice
    puts "Кости: #{dice_display}"
  end

  def show_categories(player_idx)
    used = @players[player_idx][:categories]
    puts "\nДоступные категории:"
    num = 1
    CATEGORIES.each do |cat|
      puts "  #{num}. #{cat}" unless used[cat]
      num += 1
    end
  end

  def score_category(dice, cat)
    counts = Array.new(7, 0)
    total = 0
    dice.each { |d| counts[d] += 1; total += d }
    case cat
    when 'единицы' then counts[1] * 1
    when 'двойки' then counts[2] * 2
    when 'тройки' then counts[3] * 3
    when 'четвёрки' then counts[4] * 4
    when 'пятёрки' then counts[5] * 5
    when 'шестёрки' then counts[6] * 6
    when 'три одинаковых'
      return total if counts.any? { |c| c >= 3 }
      0
    when 'четыре одинаковых'
      return total if counts.any? { |c| c >= 4 }
      0
    when 'фулл-хаус'
      return 25 if counts.any? { |c| c == 3 } && counts.any? { |c| c == 2 }
      return 25 if counts.any? { |c| c == 5 }
      0
    when 'малый стрит'
      (1..3).each do |start|
        ok = true
        4.times { |i| ok = false if counts[start+i] == 0 }
        return 30 if ok
      end
      0
    when 'большой стрит'
      ok1 = true; ok2 = true
      (1..5).each { |i| ok1 = false if counts[i] == 0 }
      (2..6).each { |i| ok2 = false if counts[i] == 0 }
      return 40 if ok1 || ok2
      0
    when 'шанс' then total
    when 'яхтзи'
      return 50 if counts.any? { |c| c == 5 }
      0
    else 0
    end
  end

  def best_category(dice, player_idx)
    best = -1
    best_cat = nil
    used = @players[player_idx][:categories]
    CATEGORIES.each do |cat|
      next if used[cat]
      score = score_category(dice, cat)
      if score > best
        best = score
        best_cat = cat
      end
    end
    [best_cat, best]
  end

  def take_turn(player_idx)
    p = @players[player_idx]
    puts "\n#{colorize("Ход: " + p[:name], :bold)}"
    @rolls_left = 3
    @keep = [false,false,false,false,false]
    roll_dice
    show_dice

    while @rolls_left > 0
      if @mode == 'ai' && player_idx == 1
        counts = Array.new(7, 0)
        @dice.each { |d| counts[d] += 1 }
        max_cnt = counts.max
        target = counts.index(max_cnt)
        if max_cnt >= 2
          @keep = @dice.map { |d| d == target }
        else
          @keep = [false,false,false,false,false]
        end
        roll_dice
        show_dice
        break
      else
        print "Введите номера кубиков для удержания (через пробел, 0 - оставить все): "
        line = gets.chomp.strip
        exit if line == 'q'
        if line == '0'
          @keep = [true,true,true,true,true]
        else
          @keep = [false,false,false,false,false]
          line.split.each do |token|
            idx = token.to_i - 1
            @keep[idx] = true if idx >= 0 && idx < 5
          end
        end
        if @rolls_left > 1
          puts "Осталось бросков: #{@rolls_left-1}"
          print "Бросить ещё? (y/n): "
          ans = gets.chomp.strip.downcase
          if ans == 'y'
            roll_dice
            show_dice
          else
            break
          end
        else
          break
        end
      end
    end

    if @mode == 'ai' && player_idx == 1
      cat, score = best_category(@dice, player_idx)
      if cat
        puts "Компьютер выбрал категорию: #{cat} (+#{score} очков)"
        p[:categories][cat] = score
        p[:score] += score
      else
        puts "Нет доступных категорий. Ход пропущен."
      end
    else
      show_categories(player_idx)
      loop do
        print "Выберите категорию (номер): "
        line = gets.chomp.strip
        exit if line == 'q'
        idx = line.to_i - 1
        if idx >= 0 && idx < CATEGORIES.size
          cat = CATEGORIES[idx]
          if p[:categories][cat]
            puts "Эта категория уже использована."
            next
          end
          score = score_category(@dice, cat)
          p[:categories][cat] = score
          p[:score] += score
          puts "Категория '#{cat}' записана! +#{score} очков"
          break
        else
          puts "Неверный номер."
        end
      end
    end
  end

  def play
    puts colorize("🎲 Добро пожаловать в Яхтзи (покер на костях)!", :bold)
    puts "Правила: бросайте 5 кубиков, выбирайте лучшую комбинацию."
    puts "У вас 3 броска за ход.\n"

    until @game_over
      p = @players[@current]
      if p[:categories].size == CATEGORIES.size
        @current = (@current + 1) % @players.size
        if @players.all? { |pl| pl[:categories].size == CATEGORIES.size }
          @game_over = true
          next
        end
        next
      end
      take_turn(@current)
      @current = (@current + 1) % @players.size
    end

    puts "\n" + colorize("🏆 ИГРА ЗАВЕРШЕНА!", :bold)
    @players.each { |p| puts "#{p[:name]}: #{p[:score]} очков" }
    winner = @players.max_by { |p| p[:score] }
    puts colorize("Победил #{winner[:name]}!", :green)
    @stats['games'] += 1
    @stats['wins'][winner[:name]] ||= 0
    @stats['wins'][winner[:name]] += 1
    save_stats
  end
end

def main
  mode = 'ai'
  names = nil
  show_stats = false
  reset = false
  i = 0
  while i < ARGV.size
    arg = ARGV[i]
    case arg
    when 'ai', 'vs' then mode = arg
    when '-n'
      names = ARGV[i+1].split(',') if i+1 < ARGV.size
      i += 1
    when '-s' then show_stats = true
    when '-r' then reset = true
    when '-h'
      puts "Usage: ruby yahtzee.rb [ai|vs] [-n names] [-s] [-r]"
      return
    end
    i += 1
  end
  if reset
    f = File.join(Dir.home, '.yahtzee_stats.json')
    File.delete(f) if File.exist?(f)
    puts "Статистика сброшена."
    return
  end
  game = Yahtzee.new(mode, names)
  if show_stats
    game.display_stats
    return
  end
  game.play
end

main if __FILE__ == $0
