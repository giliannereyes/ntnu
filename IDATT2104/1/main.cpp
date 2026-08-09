#include <iostream>
#include <vector>
#include <thread>
#include <algorithm>

bool isPrime(int num) {
    if (num < 2) return false;
    if (num == 2) return true;
    if (num % 2 == 0) return false;

    for (int d = 3; d * 1LL * d <= num; d += 2) {
        if (num % d == 0) return false;
    }
    return true;
}

void findPrimesInRange(int start, int end, std::vector<int>& localPrimes) {
    for (int x = start; x <= end; ++x) {
        if (isPrime(x)) localPrimes.push_back(x);
    }
}

int main() {
    int startNum;
    int endNum;
    int threadCount;

    std::cout << "Enter the start number: ";
    std::cin >> startNum;
    std::cout << "Enter the end number: ";
    std::cin >> endNum;
    std::cout << "Enter the number of threads: ";
    std::cin >> threadCount;

    if (startNum > endNum) std::swap(startNum, endNum);

    long long N = static_cast<long long>(endNum) - startNum + 1;
    if (N <= 0) return 0;

    if (threadCount < 1) threadCount = 1;
    if (threadCount > N) threadCount = static_cast<int>(N);

    long long base = N / threadCount;
    long long rem  = N % threadCount;

    std::vector<std::thread> threads;
    std::vector<std::vector<int>> perThreadPrimes(threadCount);

    int currentStart = startNum;

    for (int i = 0; i < threadCount; ++i) {
        long long size = base + (i < rem ? 1 : 0);
        int currentEnd = static_cast<int>(currentStart + size - 1);

        threads.emplace_back(findPrimesInRange,
                             currentStart,
                             currentEnd,
                             std::ref(perThreadPrimes[i]));

        currentStart = currentEnd + 1;
    }

    for (auto& t : threads) t.join();

    std::vector<int> allPrimes;
    for (const auto& local : perThreadPrimes) {
        allPrimes.insert(allPrimes.end(), local.begin(), local.end());
    }

    for (int p : allPrimes) {
        std::cout << p << " ";
    }
    std::cout << "\n";

    return 0;
}
