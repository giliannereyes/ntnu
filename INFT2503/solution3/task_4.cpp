#include <string>
#include <iostream>

using namespace std;

int main() {
    // a:
    string word1;
    string word2;
    string word3;

    cout << "Enter word 1:" << endl;
    cin >> word1;
    cout << "Enter word 2:" << endl;
    cin >> word2;
    cout << "Enter word 3:" << endl;
    cin >> word3;

    // b:
    string sentence = word1 + " " + word2 + " " + word3 + ".";
    cout << "Sentence: " << sentence << endl;

    // c:
    cout << "Word 1 length: " << word1.length() << endl;
    cout << "Word 2 length: " << word2.length() << endl;
    cout << "Word 3 length: " << word3.length() << endl;
    cout << "Sentence length: " << sentence.length() << endl;

    // d:
    string sentence2 = sentence;

    // e:
    if (sentence2.length() >= 13) {
        sentence2.replace(10, 3, "xxx");
    }

    cout << "Sentence 1: " << sentence << endl;
    cout << "Sentence 2: " << sentence2 << endl;

    // f:
    if (sentence.length() >= 5) {
        string sentence_start = sentence.substr(0, 5);
        cout << "Sentence: " << sentence << endl;
        cout << "Sentence start: " << sentence_start << endl;
    }

    // g:
    bool contains_hello = sentence.find("hallo") != string::npos;
    cout << "Sentence includes 'hallo': " << contains_hello << endl;

    // h:
    int count = 0;
    string search = "er";
    size_t pos = sentence.find(search);

    cout << "Occurrences of 'er' in the sentence:" << endl;
    while (pos != string::npos) {
        cout << "Found at position " << pos << endl;
        count++;
        pos = sentence.find(search, pos + search.length());
    }
    cout << "Total occurrences: " << count << endl;
}
