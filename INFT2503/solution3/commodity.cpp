#include "commodity.hpp"

using namespace std;

Commodity::Commodity(const string &name_, int id_, double price_) : name(name_), id(id_), price(price_) {}

double Commodity::get_price(double quantity) const {
    return price * quantity;
}

double Commodity::get_price_with_sales_tax(double quantity) const {
    return price * quantity * sales_tax;
}
