#include <iostream>
#include <vector>
#include <algorithm>
#include <cmath>
#include <iterator>

using namespace std;

int main() {
    vector<int> v1 = {3, 3, 12, 14, 17, 25, 30};
    vector<int> v2 = {2, 3, 12, 14, 24};

    // a
    auto it = find_if(v1.begin(), v1.end(),
        [](int n) {
            return n > 15;
        });

    if (it != v1.end()) {
        cout << "v1 includes a number > 15: " << *it << endl;
    } else {
        cout << "v1 does not include a number > 15" << endl;
    }

    // b
    bool first_five_values_are_approximately_equal = equal(v1.begin(), v1.begin() + 5, v2.begin(),
        [](int x, int y) {
            return abs(x - y) <= 2;
        });
    cout << "First five values of v1 and v2 are approximately equal: " <<
        boolalpha << first_five_values_are_approximately_equal << endl;

    bool first_four_values_are_approximately_equal = equal(v1.begin(), v1.begin() + 4, v2.begin(),
        [](int x, int y) {
            return abs(x - y) <= 2;
        });
    cout << "First four values of v1 and v2 are approximately equal: " <<
        boolalpha << first_four_values_are_approximately_equal << endl;

    // c
    vector<int> v1_with_odd_numbers_replaced;
    replace_copy_if(v1.begin(), v1.end(), back_inserter(v1_with_odd_numbers_replaced),
        [](int x) {
            return x % 2 != 0;
        }, 100);
    v1 = v1_with_odd_numbers_replaced;

    cout << "v1 after replacing odd numbers with 100: ";
    for (int number : v1) {
        cout << number << " ";
    }
}
