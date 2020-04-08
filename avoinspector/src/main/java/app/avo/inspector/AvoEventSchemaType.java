package app.avo.inspector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class AvoEventSchemaType {

    @NonNull abstract java.lang.String getName();

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
    public java.lang.String toString() {
        return getName();
    }

    static class Int extends AvoEventSchemaType {
        @NonNull
        @Override
        java.lang.String getName() {
            return "int";
        }
    }

    static class Float extends AvoEventSchemaType {
        @NonNull
        @Override
        java.lang.String getName() {
            return "float";
        }
    }

    static class Boolean extends AvoEventSchemaType {
        @NonNull
        @Override
        java.lang.String getName() {
            return "boolean";
        }
    }

    static class String extends AvoEventSchemaType {
        @NonNull
        @Override
        java.lang.String getName() {
            return "string";
        }
    }

    static class Null extends AvoEventSchemaType {
        @NonNull
        @Override
        java.lang.String getName() {
            return "null";
        }
    }

    static class List extends AvoEventSchemaType {
        @NonNull Set<AvoEventSchemaType> subtypes;

        List(@NonNull Set<AvoEventSchemaType> subtypes) {
            this.subtypes = subtypes;
        }

        @NonNull
        @Override
        java.lang.String getName() {
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

    static class AvoObject extends AvoEventSchemaType {

        @NonNull Map<java.lang.String, AvoEventSchemaType> children;

        AvoObject(@NonNull Map<java.lang.String, AvoEventSchemaType> children) {
            this.children = children;
        }

        @NonNull
        @Override
        java.lang.String getName() {
            java.lang.String jsonArrayString = Util.remapProperties(children).toString();
            return jsonArrayString.substring(1, jsonArrayString.length() - 1);
        }
    }

    static class Unknown extends AvoEventSchemaType {

        @NonNull
        @Override
        java.lang.String getName() {
            return "unknown";
        }
    }
}
