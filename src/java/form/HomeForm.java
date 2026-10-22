package form;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.Home;

/** Fields and server-side checks of the home form (id is empty when creating). */
public final class HomeForm {

    public static final String[] FIELDS = {"id", "homeName", "address", "ownerUserId"};

    private HomeForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        return Fields.empty(FIELDS);
    }

    public static Map<String, String> of(Home home) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(home.getHomeId()));
        form.put("homeName", home.getHomeName());
        form.put("address", Fields.text(home.getAddress()));
        form.put("ownerUserId", Fields.text(home.getOwnerUserId()));
        return form;
    }

    /** Fills errors (field -> message) and returns the home to save. */
    public static Home validate(Map<String, String> form, Map<String, String> errors) {
        Home home = new Home();
        home.setHomeId(Fields.idOf(form));
        home.setHomeName(Fields.required(form, errors, "homeName", "home name", 80));
        home.setAddress(Fields.optional(form, errors, "address", 200));
        home.setOwnerUserId(Fields.optionalId(form, errors, "ownerUserId"));
        return home;
    }
}
