#include <iostream>
#include <fstream>
#include <cstdlib>

using namespace std;

void read_temperatures(double temperatures[], int length);

int main() {
    const int length = 5;
    double temperatures[length];
    read_temperatures(temperatures, length);

    int under_10 = 0;
    int between_10_and_20 = 0;
    int over_20 = 0;

    for (int i = 0; i < length; i++) {
        if (temperatures[i] < 10) {
            under_10++;
        } else if (temperatures[i] <= 20) {
            between_10_and_20++;
        } else {
            over_20++;
        }
    }

    cout << "Antall under 10 er " << under_10 << endl;
    cout << "Antall mellom 10 og 20 er " << between_10_and_20 << endl;
    cout << "Antall over 20 er " << over_20 << endl;
}

void read_temperatures(double temperatures[], int length) {
    ifstream temperatures_file;
    temperatures_file.open("temperatures.txt");
    if (!temperatures_file) {
        cout << "Error opening temperatures.txt" << endl;
        exit(EXIT_FAILURE);
    }

    for (int i = 0; i < length; i++) {
        temperatures_file >> temperatures[i];
    }

    temperatures_file.close();
}

