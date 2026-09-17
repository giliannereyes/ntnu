#include <gtkmm.h>

class Window : public Gtk::Window {
public:
    Gtk::Box box;
    Gtk::Entry first_name_entry;
    Gtk::Entry last_name_entry;
    Gtk::Button button;
    Gtk::Label label;

    Window() : box(Gtk::Orientation::ORIENTATION_VERTICAL), button("Combine") {
        set_title("Name combiner");
        set_default_size(300, 120);

        first_name_entry.set_placeholder_text("First name");
        last_name_entry.set_placeholder_text("Last name");
        button.set_sensitive(false);

        box.set_margin_top(10);
        box.set_margin_bottom(10);
        box.set_margin_start(10);
        box.set_margin_end(10);
        box.set_spacing(10);

        box.pack_start(first_name_entry);
        box.pack_start(last_name_entry);
        box.pack_start(button);
        box.pack_start(label);

        add(box);
        show_all();

        auto has_text = [](const Gtk::Entry &entry) {
            return entry.get_text().find_first_not_of(" \t\n\r") != Glib::ustring::npos;
        };

        auto update_button = [this, has_text]() {
            button.set_sensitive(has_text(first_name_entry) && has_text(last_name_entry));
        };

        first_name_entry.signal_changed().connect(update_button);
        last_name_entry.signal_changed().connect(update_button);

        button.signal_clicked().connect([this]() {
            label.set_text(first_name_entry.get_text() + " " + last_name_entry.get_text());
        });
    }
};

int main() {
    auto app = Gtk::Application::create();
    Window window;
    return app->run(window);
}
