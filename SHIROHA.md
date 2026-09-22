# spark for Shiroha 26.2

This branch tracks `lucko/spark-extra-platforms` and adds a narrow compatibility fix for Shiroha 26.2.

## Fix

Shiroha can retire a region between spark's region snapshot and statistics sampling. A retired region returns `null` from `ThreadedRegion#getData`. The Folia TPS and MSPT collectors now skip retired regions instead of throwing a `NullPointerException`.

## Compatibility

- spark base: 1.10.186
- Shiroha: 26.2, verified against commit `9daa83b`
- Java runtime: 25

Only the Folia artifact is branded as a Shiroha build. Other platform modules remain unchanged.

## Hot reload

- `/spark reload` reloads `config.json` and restarts spark's runtime services without replacing the plugin classloader. Permission: `spark.reload` (operators by default).
- Full plugin unload/reload tools can discard the old classloader without leaving spark's shared monitoring executor running. This path was tested with PlugManX 3.1.0-Beta.2.
- Shiroha-specific plugin metadata and hot-reload feedback are localized in Chinese. Command names and permission nodes remain unchanged.

Reloading stops any active profiling session. The background profiler is restarted when it is enabled in the configuration. Run `/spark healthreport` after a full plugin reload to confirm that the new instance is responding.

## Upstream

The unmodified upstream project is available at <https://github.com/lucko/spark-extra-platforms>.
