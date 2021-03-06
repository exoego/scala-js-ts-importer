declare module enumtype {
    export enum Color {
        Red, Green, Blue
    }

    export enum Button {
        Submit = "submit",
        Reset = "reset",
        Button = "button"
    }

    export enum Mixed {
        EMPTY, NUMERIC = 2, STRING = "string", NEGATIVE = -1
    }

    export const enum RenderLineNumbersType {
        Off = 0,
        On = 1,
    }
}
