import next from "eslint-config-next";

/**
 * ESLint flat config.
 *
 * Next 16 ships a native flat config, so `FlatCompat` is not used here. The
 * compat shim exists for older shareable configs and it cannot resolve this
 * one without a circular-reference error.
 */
const config = [
  ...next,
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts", "public/**"],
  },
];

export default config;