package app.avo.inspector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public abstract class AvoEventSchemaType {

    @NonNull abstract String getName();

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof AvoEventSchemaType) {
            return getName().equals(((AvoEventSchemaType) obj).getName());
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return getName();
    }

    public static class AvoInt extends AvoEventSchemaType {
        @NonNull
        @Override
        String getName() {
            return "int";
        }
    }

    public static class AvoFloat extends AvoEventSchemaType {
        @NonNull
        @Override
        String getName() {
            return "float";
        }
    }

    public static class AvoBoolean extends AvoEventSchemaType {
        @NonNull
        @Override
        String getName() {
            return "boolean";
        }
    }

    public static class AvoString extends AvoEventSchemaType {
        @NonNull
        @Override
        String getName() {
            return "string";
        }
    }

    public static class AvoNull extends AvoEventSchemaType {
        @NonNull
        @Override
        String getName() {
            return "null";
        }
    }

    public static class AvoList extends AvoEventSchemaType {
        @NonNull Set<AvoEventSchemaType> subtypes;

        AvoList(@NonNull Set<AvoEventSchemaType> subtypes) {
            this.subtypes = subtypes;
        }

        @NonNull
        @Override
        String getName() {
            StringBuilder types = new StringBuilder();

            boolean first = true;
            for (AvoEventSchemaType subtype: subtypes) {
                if (!first) {
                    types.append("|");
                }

                types.append(subtype.getName());
                first = false;
            }

            return "list<" + types + ">";
        }
    }

    public static class AvoObject extends AvoEventSchemaType {

        @NonNull Map<String, AvoEventSchemaType> children;

        AvoObject(@NonNull Map<String, AvoEventSchemaType> children) {
            this.children = children;
        }

        @NonNull
        @Override
        String getName() {
            String jsonArrayString = Util.remapProperties(children).toString();
            return jsonArrayString.substring(1, jsonArrayString.length() - 1);
        }
    }

    public static class AvoUnknownType extends AvoEventSchemaType {

        @NonNull
        @Override
        String getName() {
            return "unknown";
        }
    }
}
