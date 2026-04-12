/*
 * Sorting Algorithm Visualizer
 * Visualizes Bubble Sort, Merge Sort, and Quick Sort step-by-step in the console.
 * Displays a bar chart that updates live as sorting progresses.
 */

#include <iostream>
#include <vector>
#include <string>
#include <chrono>
#include <thread>
#include <algorithm>
#include <random>
#include <iomanip>
#include <limits>

// ─── ANSI escape helpers ──────────────────────────────────────────────────────

namespace Color {
    const std::string RESET   = "\033[0m";
    const std::string BOLD    = "\033[1m";
    const std::string RED     = "\033[31m";
    const std::string GREEN   = "\033[32m";
    const std::string YELLOW  = "\033[33m";
    const std::string BLUE    = "\033[34m";
    const std::string MAGENTA = "\033[35m";
    const std::string CYAN    = "\033[36m";
    const std::string WHITE   = "\033[37m";
    const std::string BG_BLUE = "\033[44m";
}

// Move cursor to row, col (1-indexed)
void moveCursor(int row, int col) {
    std::cout << "\033[" << row << ";" << col << "H";
}

void clearScreen() {
    std::cout << "\033[2J\033[H";
    std::cout.flush();
}

void hideCursor() { std::cout << "\033[?25l"; }
void showCursor() { std::cout << "\033[?25h"; }

// ─── Visualization constants ───────────────────────────────────────────────────

static const int DISPLAY_HEIGHT = 20;   // max bar height in rows
static const int BAR_WIDTH      = 3;    // characters per bar
static const int DELAY_MS       = 60;   // ms between animation frames

// Role colours for individual bars
enum class Role { NORMAL, COMPARING, PIVOT, SORTED, MERGING };

struct BarState {
    int   value;
    Role  role;
};

// ─── Draw helpers ─────────────────────────────────────────────────────────────


void drawTitle(const std::string& algo, long long elapsed_us, int comparisons, int swaps) {
    moveCursor(1, 1);
    std::cout << Color::BOLD << Color::CYAN
              << "  ╔══════════════════════════════════════════════════════╗\n"
              << "  ║        SORTING ALGORITHM VISUALIZER                 ║\n"
              << "  ╚══════════════════════════════════════════════════════╝"
              << Color::RESET << "\n";

    moveCursor(4, 1);
    std::cout << Color::BOLD << "  Algorithm : " << Color::YELLOW << std::left << std::setw(15) << algo << Color::RESET;
    std::cout << Color::BOLD << "  Time : " << Color::GREEN
              << std::right << std::setw(8) << elapsed_us << " µs" << Color::RESET;
    std::cout << Color::BOLD << "  Comparisons : " << Color::MAGENTA
              << std::setw(6) << comparisons << Color::RESET;
    std::cout << Color::BOLD << "  Swaps : " << Color::RED
              << std::setw(6) << swaps << Color::RESET << "\n";
}

void drawLegend() {
    moveCursor(6, 1);
    std::cout << "  "
              << Color::WHITE  << "█ Normal  "
              << Color::YELLOW << "█ Comparing  "
              << Color::RED    << "█ Pivot/Active  "
              << Color::GREEN  << "█ Sorted  "
              << Color::CYAN   << "█ Merging"
              << Color::RESET  << "\n";
}

void drawArray(const std::vector<BarState>& bars, int maxVal) {
    int n = (int)bars.size();
    // rows 8..(8+DISPLAY_HEIGHT-1) are the chart area
    const int chartTop = 8;

    for (int row = 0; row < DISPLAY_HEIGHT; row++) {
        moveCursor(chartTop + row, 1);
        std::cout << "  ";
        for (int i = 0; i < n; i++) {
            // How many filled rows does this bar occupy (from bottom)?
            int filled = (bars[i].value * DISPLAY_HEIGHT) / maxVal;
            // Current row from top: row 0 is top, row DISPLAY_HEIGHT-1 is bottom
            int rowFromBottom = DISPLAY_HEIGHT - 1 - row;

            std::string color;
            switch (bars[i].role) {
                case Role::COMPARING: color = Color::YELLOW;  break;
                case Role::PIVOT:     color = Color::RED;     break;
                case Role::SORTED:    color = Color::GREEN;   break;
                case Role::MERGING:   color = Color::CYAN;    break;
                default:              color = Color::WHITE;   break;
            }

            if (rowFromBottom < filled) {
                std::cout << color << std::string(BAR_WIDTH, '#') << Color::RESET;
            } else {
                std::cout << std::string(BAR_WIDTH, ' ');
            }
            std::cout << ' '; // gap between bars
        }
        std::cout << "\n";
    }

    // Value axis bottom line
    moveCursor(chartTop + DISPLAY_HEIGHT, 1);
    std::cout << "  " << std::string(n * (BAR_WIDTH + 1), '-') << "\n";

    std::cout.flush();
}

// Draw array, sleep, measure time
void visualStep(std::vector<BarState>& bars, int maxVal,
                const std::string& algo,
                std::chrono::steady_clock::time_point start,
                int comparisons, int swaps)
{
    auto now     = std::chrono::steady_clock::now();
    long long us = std::chrono::duration_cast<std::chrono::microseconds>(now - start).count();
    drawTitle(algo, us, comparisons, swaps);
    drawLegend();
    drawArray(bars, maxVal);
    std::this_thread::sleep_for(std::chrono::milliseconds(DELAY_MS));
}

// ─── Sorting algorithms ────────────────────────────────────────────────────────

// Bubble Sort
std::pair<int,int> bubbleSort(std::vector<int>& arr, int maxVal,
                               const std::string& algoName,
                               std::chrono::steady_clock::time_point start)
{
    int n = (int)arr.size();
    int comparisons = 0, swaps = 0;
    std::vector<BarState> bars(n);

    int sortedFromDefault = n;
    auto makeState = [&](int cmp1 = -1, int cmp2 = -1, int sortedFrom = -1) {
        if (sortedFrom == -1) sortedFrom = sortedFromDefault;
        for (int i = 0; i < n; i++) {
            bars[i].value = arr[i];
            if (i >= sortedFrom)       bars[i].role = Role::SORTED;
            else if (i == cmp1 || i == cmp2) bars[i].role = Role::COMPARING;
            else                       bars[i].role = Role::NORMAL;
        }
    };

    for (int i = 0; i < n - 1; i++) {
        bool swapped = false;
        for (int j = 0; j < n - i - 1; j++) {
            comparisons++;
            makeState(j, j + 1, n - i);
            visualStep(bars, maxVal, algoName, start, comparisons, swaps);

            if (arr[j] > arr[j + 1]) {
                std::swap(arr[j], arr[j + 1]);
                swaps++;
            }
            swapped = true;
        }
        if (!swapped) break;
    }

    // Mark all sorted
    for (int i = 0; i < n; i++) { bars[i].value = arr[i]; bars[i].role = Role::SORTED; }
    visualStep(bars, maxVal, algoName, start, comparisons, swaps);

    return {comparisons, swaps};
}

// Merge Sort helpers
static std::vector<BarState>* g_bars_ptr = nullptr;
static int*                   g_cmp_ptr  = nullptr;
static int*                   g_swp_ptr  = nullptr;
static int                    g_maxVal   = 0;
static std::string             g_algoName;
static std::chrono::steady_clock::time_point g_start;

void mergeStep(std::vector<int>& arr, int l, int m, int r) {
    int n1 = m - l + 1, n2 = r - m;
    std::vector<int> L(arr.begin() + l, arr.begin() + l + n1);
    std::vector<int> R(arr.begin() + m + 1, arr.begin() + m + 1 + n2);

    int i = 0, j = 0, k = l;
    while (i < n1 && j < n2) {
        (*g_cmp_ptr)++;
        // Highlight merge region
        for (int x = 0; x < (int)g_bars_ptr->size(); x++) {
            (*g_bars_ptr)[x].value = arr[x];
            if (x >= l && x <= r) (*g_bars_ptr)[x].role = Role::MERGING;
            else                  (*g_bars_ptr)[x].role = Role::NORMAL;
        }
        visualStep(*g_bars_ptr, g_maxVal, g_algoName, g_start, *g_cmp_ptr, *g_swp_ptr);

        if (L[i] <= R[j]) arr[k++] = L[i++];
        else               { arr[k++] = R[j++]; (*g_swp_ptr)++; }
    }
    while (i < n1) arr[k++] = L[i++];
    while (j < n2) arr[k++] = R[j++];
}

void mergeSortRec(std::vector<int>& arr, int l, int r) {
    if (l >= r) return;
    int m = l + (r - l) / 2;
    mergeSortRec(arr, l, m);
    mergeSortRec(arr, m + 1, r);
    mergeStep(arr, l, m, r);
}

std::pair<int,int> mergeSort(std::vector<int>& arr, int maxVal,
                              const std::string& algoName,
                              std::chrono::steady_clock::time_point start)
{
    int n = (int)arr.size();
    int comparisons = 0, swaps = 0;
    std::vector<BarState> bars(n);
    for (int i = 0; i < n; i++) { bars[i].value = arr[i]; bars[i].role = Role::NORMAL; }

    g_bars_ptr = &bars;
    g_cmp_ptr  = &comparisons;
    g_swp_ptr  = &swaps;
    g_maxVal   = maxVal;
    g_algoName = algoName;
    g_start    = start;

    mergeSortRec(arr, 0, n - 1);

    for (int i = 0; i < n; i++) { bars[i].value = arr[i]; bars[i].role = Role::SORTED; }
    visualStep(bars, maxVal, algoName, start, comparisons, swaps);

    return {comparisons, swaps};
}

// Quick Sort helpers
static std::vector<BarState>* g_qbars_ptr = nullptr;
static int*                   g_qcmp_ptr  = nullptr;
static int*                   g_qswp_ptr  = nullptr;
static int                    g_qmaxVal   = 0;
static std::string             g_qalgoName;
static std::chrono::steady_clock::time_point g_qstart;

int partition(std::vector<int>& arr, int low, int high) {
    int pivot = arr[high];
    int i = low - 1;
    int n = (int)arr.size();

    for (int j = low; j < high; j++) {
        (*g_qcmp_ptr)++;
        for (int x = 0; x < n; x++) {
            (*g_qbars_ptr)[x].value = arr[x];
            (*g_qbars_ptr)[x].role  = Role::NORMAL;
        }
        (*g_qbars_ptr)[high].role = Role::PIVOT;
        (*g_qbars_ptr)[j].role    = Role::COMPARING;
        if (i >= low) (*g_qbars_ptr)[i].role = Role::COMPARING;

        visualStep(*g_qbars_ptr, g_qmaxVal, g_qalgoName, g_qstart, *g_qcmp_ptr, *g_qswp_ptr);

        if (arr[j] < pivot) {
            i++;
            std::swap(arr[i], arr[j]);
            (*g_qswp_ptr)++;
        }
    }
    std::swap(arr[i + 1], arr[high]);
    (*g_qswp_ptr)++;
    return i + 1;
}

void quickSortRec(std::vector<int>& arr, int low, int high) {
    if (low < high) {
        int pi = partition(arr, low, high);
        quickSortRec(arr, low, pi - 1);
        quickSortRec(arr, pi + 1, high);
    }
}

std::pair<int,int> quickSort(std::vector<int>& arr, int maxVal,
                              const std::string& algoName,
                              std::chrono::steady_clock::time_point start)
{
    int n = (int)arr.size();
    int comparisons = 0, swaps = 0;
    std::vector<BarState> bars(n);
    for (int i = 0; i < n; i++) { bars[i].value = arr[i]; bars[i].role = Role::NORMAL; }

    g_qbars_ptr = &bars;
    g_qcmp_ptr  = &comparisons;
    g_qswp_ptr  = &swaps;
    g_qmaxVal   = maxVal;
    g_qalgoName = algoName;
    g_qstart    = start;

    quickSortRec(arr, 0, n - 1);

    for (int i = 0; i < n; i++) { bars[i].value = arr[i]; bars[i].role = Role::SORTED; }
    visualStep(bars, maxVal, algoName, start, comparisons, swaps);

    return {comparisons, swaps};
}

// ─── Results summary ───────────────────────────────────────────────────────────

struct Result {
    std::string name;
    long long   time_us;
    int         comparisons;
    int         swaps;
};

void printSummary(const std::vector<Result>& results) {
    clearScreen();
    std::cout << Color::BOLD << Color::CYAN
              << "\n  ╔══════════════════════════════════════════════════════════╗\n"
              << "  ║              PERFORMANCE COMPARISON SUMMARY             ║\n"
              << "  ╚══════════════════════════════════════════════════════════╝\n\n"
              << Color::RESET;

    std::cout << Color::BOLD
              << "  " << std::left  << std::setw(15) << "Algorithm"
              << std::right << std::setw(12) << "Time (µs)"
              << std::setw(15) << "Comparisons"
              << std::setw(10) << "Swaps"
              << Color::RESET << "\n";
    std::cout << "  " << std::string(50, '-') << "\n";

    // Find best (min time)
    long long minTime = std::numeric_limits<long long>::max();
    for (auto& r : results) minTime = std::min(minTime, r.time_us);

    for (auto& r : results) {
        bool best = (r.time_us == minTime);
        std::cout << "  "
                  << (best ? Color::GREEN : "") << Color::BOLD
                  << std::left  << std::setw(15) << r.name
                  << Color::RESET
                  << std::right << std::setw(12) << r.time_us
                  << std::setw(15) << r.comparisons
                  << std::setw(10) << r.swaps;
        if (best) std::cout << Color::GREEN << "  ← fastest" << Color::RESET;
        std::cout << "\n";
    }

    std::cout << "\n  " << std::string(50, '-') << "\n";
    std::cout << Color::BOLD << "\n  Press Enter to exit..." << Color::RESET << std::flush;
    std::cin.get();
}

// ─── Menu ─────────────────────────────────────────────────────────────────────

int getIntInput(const std::string& prompt, int lo, int hi) {
    int val;
    while (true) {
        std::cout << prompt;
        if (std::cin >> val && val >= lo && val <= hi) {
            std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
            return val;
        }
        std::cin.clear();
        std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
        std::cout << "  Please enter a number between " << lo << " and " << hi << ".\n";
    }
}

void printMenu() {
    std::cout << Color::BOLD << Color::CYAN
              << "\n  ╔══════════════════════════════════════════════════════╗\n"
              << "  ║        SORTING ALGORITHM VISUALIZER                 ║\n"
              << "  ╚══════════════════════════════════════════════════════╝\n\n"
              << Color::RESET;

    std::cout << Color::BOLD << "  Choose what to run:\n\n" << Color::RESET;
    std::cout << "   1. " << Color::YELLOW  << "Bubble Sort"  << Color::RESET << "\n";
    std::cout << "   2. " << Color::CYAN    << "Merge Sort"   << Color::RESET << "\n";
    std::cout << "   3. " << Color::RED     << "Quick Sort"   << Color::RESET << "\n";
    std::cout << "   4. " << Color::GREEN   << "All three (with performance comparison)" << Color::RESET << "\n";
    std::cout << "   5. " << Color::WHITE   << "Exit"         << Color::RESET << "\n\n";
}

std::vector<int> generateArray(int n, int maxVal) {
    std::vector<int> arr(n);
    std::mt19937 rng(std::random_device{}());
    std::uniform_int_distribution<int> dist(1, maxVal);
    for (auto& v : arr) v = dist(rng);
    return arr;
}

// ─── Run a single algorithm ────────────────────────────────────────────────────

Result runAlgorithm(int choice, const std::vector<int>& original, int maxVal) {
    std::vector<int> arr = original;
    std::string name;
    std::pair<int,int> stats;

    clearScreen();
    hideCursor();

    auto start = std::chrono::steady_clock::now();

    if      (choice == 1) { name = "Bubble Sort"; stats = bubbleSort(arr, maxVal, name, start); }
    else if (choice == 2) { name = "Merge Sort";  stats = mergeSort (arr, maxVal, name, start); }
    else                  { name = "Quick Sort";  stats = quickSort (arr, maxVal, name, start); }

    auto end    = std::chrono::steady_clock::now();
    long long us = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();

    showCursor();

    // Pause a moment so user sees the final sorted state
    std::this_thread::sleep_for(std::chrono::milliseconds(600));

    return {name, us, stats.first, stats.second};
}

// ─── Main ─────────────────────────────────────────────────────────────────────

int main() {
    clearScreen();

    std::cout << Color::BOLD << Color::CYAN
              << "\n  ╔══════════════════════════════════════════════════════╗\n"
              << "  ║        SORTING ALGORITHM VISUALIZER                 ║\n"
              << "  ╠══════════════════════════════════════════════════════╣\n"
              << "  ║  Watch Bubble, Merge & Quick Sort animate live!      ║\n"
              << "  ╚══════════════════════════════════════════════════════╝\n\n"
              << Color::RESET;

    int arraySize = getIntInput("  Array size (5-30): ", 5, 30);
    int maxVal    = 50;

    while (true) {
        clearScreen();
        printMenu();
        int choice = getIntInput("  Enter choice (1-5): ", 1, 5);

        if (choice == 5) {
            clearScreen();
            std::cout << Color::BOLD << Color::GREEN
                      << "\n  Thanks for using the Sorting Visualizer!\n\n"
                      << Color::RESET;
            break;
        }

        std::vector<int> original = generateArray(arraySize, maxVal);

        if (choice == 4) {
            // Run all three on the same data
            std::vector<Result> results;
            for (int alg = 1; alg <= 3; alg++) {
                results.push_back(runAlgorithm(alg, original, maxVal));
                if (alg < 3) {
                    clearScreen();
                    std::cout << Color::BOLD << Color::WHITE
                              << "\n  Next algorithm starting in 2 seconds...\n"
                              << Color::RESET << std::flush;
                    std::this_thread::sleep_for(std::chrono::seconds(2));
                }
            }
            printSummary(results);
        } else {
            Result r = runAlgorithm(choice, original, maxVal);
            // Brief summary for single algorithm
            clearScreen();
            std::cout << Color::BOLD << Color::CYAN
                      << "\n  ╔══════════════════════════════╗\n"
                      << "  ║         RESULT               ║\n"
                      << "  ╚══════════════════════════════╝\n\n"
                      << Color::RESET;
            std::cout << "  Algorithm   : " << Color::YELLOW << r.name        << Color::RESET << "\n";
            std::cout << "  Time        : " << Color::GREEN  << r.time_us << " µs" << Color::RESET << "\n";
            std::cout << "  Comparisons : " << Color::MAGENTA<< r.comparisons << Color::RESET << "\n";
            std::cout << "  Swaps       : " << Color::RED    << r.swaps       << Color::RESET << "\n\n";
            std::cout << Color::BOLD << "  Press Enter to return to menu..." << Color::RESET << std::flush;
            std::cin.get();
        }
    }

    return 0;
}
