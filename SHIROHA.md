# spark for Shiroha 26.2

This branch tracks `lucko/spark-extra-platforms` and adds a narrow compatibility fix for Shiroha 26.2.

## Fix

Shiroha can retire a region between spark's region snapshot and statistics sampling. A retired region returns `null` from `ThreadedRegion#getData`. The Folia TPS and MSPT collectors now skip retired regions instead of throwing a `NullPointerException`.

## Compatibility

- spark base: 1.10.186
- Shiroha: 26.2, verified against commit `9daa83b`
- Java runtime: 25

Only the Folia artifact is branded as a Shiroha build. Other platform modules remain unchanged.

## Upstream

The unmodified upstream project is available at <https://github.com/lucko/spark-extra-platforms>.
