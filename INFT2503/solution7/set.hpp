#pragma once
#include <vector>
#include <ostream>

class Set {
public:
    Set();
    Set(const Set &other) = default;
    Set operator+(const Set &other) const;
    Set &operator+=(int number);
    Set &operator=(const Set &other) = default;
    friend std::ostream &operator<<(std::ostream &out, const Set &set);

private:
    std::vector<int> numbers;
};
