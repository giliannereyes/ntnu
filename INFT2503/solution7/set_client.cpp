#include "set.hpp"
#include <iostream>

using namespace std;

int main() {
    Set empty_set;
    cout << "Tom mengde: " << empty_set << endl;

    Set set1;
    set1 += 1;
    set1 += 4;
    set1 += 3;
    set1 += 4; // Duplicate

    Set set2;
    set2 += 4;
    set2 += 7;

    cout << "Mengde 1: " << set1 << endl;
    cout << "Mengde 2: " << set2 << endl;

    Set union_set = set1 + set2;
    cout << "Union: " << union_set << endl;

    Set copied_set;
    copied_set = set1;
    cout << "Kopi av mengde 1: " << copied_set << endl;

    return 0;
}
