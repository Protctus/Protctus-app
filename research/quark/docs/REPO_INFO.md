# Quark-Engine — Official Repository Information

## Repository Identity

| Field | Value |
|-------|-------|
| **Repository** | [ev-flow/quark-engine](https://github.com/ev-flow/quark-engine) |
| **Organization** | ev-flow (GitHub Org) |
| **Created** | October 22, 2019 |
| **Last Push** | May 13, 2026 |
| **Last Repo Update** | May 15, 2026 |
| **Stars** | 1,675 |
| **Forks** | 201 |
| **Watchers** | 39 |
| **Open Issues** | 77 |
| **Primary Language** | Python |
| **License** | GNU General Public License v3.0 (GPL-3.0) |
| **Default Branch** | master |
| **Archived** | No |
| **Homepage** | https://doc.quark-engine.com |

## Topics
`android`, `blackbox`, `blackhat`, `defcon`, `malware-analysis`, `malware-detection`

## Description
An Obfuscation-Neglect Android Malware Scoring System.

## Key Maintainers
- **YuShiang Dang** (pulorsok@gmail.com)
- **ShengFeng Lu**
- **KunYu Chen**

## Repository Health
- **Active maintenance**: Yes — pushed May 2026
- **Documentation**: ReadTheDocs at doc.quark-engine.com
- **CI/CD**: Travis CI + GitHub Actions
- **Test coverage**: Present (`tests/` directory)
- **Conference presentations**: BlackHat, DEFCON, HITB

## Related Repositories
- **Quark Rules**: [ev-flow/quark-rules](https://github.com/ev-flow/quark-rules) — 274 behavioral detection rules
- **Label Descriptions**: `label_desc.csv` mapping rule labels to human descriptions

## License Warning

> [!WARNING]
> Quark-Engine is licensed under **GPL-3.0**. This is a copyleft license.
> Any derivative work MUST also be released under GPL-3.0.
> For Protctus: we must either:
> - Release Protctus scanning module under GPL-3.0
> - Completely rewrite the behavioral analysis logic (inspired by, not derived from, Quark)
> - Use Quark only as a research reference and implement clean-room behavioral detection

## Verification
- ✅ Official repository confirmed via GitHub org `ev-flow`
- ✅ Active development (pushed May 2026)
- ✅ Python 3.10+ requirement
- ✅ 274 behavioral detection rules available
- ⚠️ GPL-3.0 license — requires legal consideration

## Clone Commands Used
```bash
git clone --depth 1 https://github.com/ev-flow/quark-engine.git research/quark/source
git clone --depth 1 https://github.com/ev-flow/quark-rules.git research/quark/rules
```
