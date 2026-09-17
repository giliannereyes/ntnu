#include <iostream>
#include <vector>
#include <algorithm>

using namespace std;

int main() {
    vector<double>  numbers;
    for (int i = 1; i <= 5; i++) {
        numbers.emplace_back(i);
    }

    double first = numbers.front();
    double last = numbers.back();
    cout << "Front: " << first << endl;
    cout << "Back: " << last << endl;

    numbers.emplace(numbers.begin() + 1, 0.0);
    cout << "Front: " << numbers.front() << endl;

    auto number_5 = find(numbers.begin(), numbers.end(), 5.0);

    if (number_5 == numbers.end()) {
        cout << "There is no such element in the list." << endl;
    } else {
        cout << "The number was found: " << *number_5 << endl;
    }
}