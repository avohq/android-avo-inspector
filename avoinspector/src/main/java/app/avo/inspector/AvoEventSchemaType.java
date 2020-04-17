package app.avo.inspector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public abstract class AvoEventSchemaType {

    @NonNull abstract String getReportedName();

    @NonNull protected String getReadableName() {
        return getReportedName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof AvoEventSchemaType) {
            return getReportedName().equals(((AvoEventSchemaType) obj).getReportedName());
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return getReportedName().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return getReportedName();
    }

    public static class AvoInt extends AvoEventSchemaType {
        @NonNull
        @Override
        String getReportedName() {
            return "int";
        }
    }

    public static class AvoFloat extends AvoEventSchemaType {
        @NonNull
        @Override
        String getReportedName() {
            return "float";
        }
    }

    public static class AvoBoolean extends AvoEventSchemaType {
        @NonNull
        @Override
        String getReportedName() {
            return "boolean";
        }
    }

    public static class AvoString extends AvoEventSchemaType {
        @NonNull
        @Override
        String getReportedName() {
            return "string";
        }
    }

    public static class AvoNull extends AvoEventSchemaType {
        @NonNull
        @Override
        String getReportedName() {
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
        String getReportedName() {
            StringBuilder types = new StringBuilder();

            boolean first = true;
            for (AvoEventSchemaType subtype: subtypes) {
                if (!first) {
                    types.append("|");
                }

                types.append(subtype.getReportedName());
                first = false;
            }

            return "list<" + types + ">";
        }

        @NonNull
        @Override
        protected String getReadableName() {
            StringBuilder types = new StringBuilder();

            boolean first = true;
            for (AvoEventSchemaType subtype: subtypes) {
                if (!first) {
                    types.append("|");
                }

                types.append(subtype.getReadableName());
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
        String getReportedName() {
            String jsonArrayString = Util.remapProperties(children).toString();
            return jsonArrayString.substring(1, jsonArrayString.length() - 1);
        }

        @NonNull
        @Override
        protected String getReadableName() {
            String jsonArrayString = Util.readableJsonProperties(children);
            return jsonArrayString;
        }
    }

    public static class AvoUnknownType extends AvoEventSchemaType {

        @NonNull
        @Override
        String getReportedName() {
            return "unknown";
        }
    }
}
