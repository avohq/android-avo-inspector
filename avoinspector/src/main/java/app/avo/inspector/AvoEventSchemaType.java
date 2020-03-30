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
        public java.lang.String getName() {
            return "int";
        }
    }

    static class Float extends AvoEventSchemaType {
        @NonNull
        @Override
        public java.lang.String getName() {
            return "float";
        }
    }

    static class Boolean extends AvoEventSchemaType {
        @NonNull
        @Override
        public java.lang.String getName() {
            return "boolean";
        }
    }

    static class String extends AvoEventSchemaType {
        @NonNull
        @Override
        public java.lang.String getName() {
            return "string";
        }
    }

    static class Null extends AvoEventSchemaType {
        @NonNull
        @Override
        public java.lang.String getName() {
            return "null";
        }
    }

    static class List extends AvoEventSchemaType {
        @NonNull Set<AvoEventSchemaType> subtypes;

        public List(@NonNull Set<AvoEventSchemaType> subtypes) {
            this.subtypes = subtypes;
        }

        @NonNull
        @Override
        public java.lang.String getName() {
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

        public AvoObject(@NonNull Map<java.lang.String, AvoEventSchemaType> children) {
            this.children = children;
        }

        @NonNull
        @Override
        java.lang.String getName() {
            StringBuilder objectSchema = new StringBuilder("{");

            for (java.lang.String childName: children.keySet()) {
                AvoEventSchemaType childType = children.get(childName);
                if (childType == null) {
                    continue;
                }

                objectSchema.append("\"");
                objectSchema.append(childName);
                objectSchema.append("\"");
                objectSchema.append(": ");
                if (childType instanceof AvoEventSchemaType.AvoObject) {
                    objectSchema.append(childType.getName());
                    objectSchema.append(",");
                } else {
                    objectSchema.append("\"");
                    objectSchema.append(childType.getName());
                    objectSchema.append("\",");
                }
            }

            objectSchema.append("}");

            return objectSchema.toString();
        }
    }

    static class Unknown extends AvoEventSchemaType {

        @NonNull
        @Override
        public java.lang.String getName() {
            return "unknown";
        }
    }
}
