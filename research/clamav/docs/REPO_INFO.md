# ClamAV — Official Repository Information

## Repository Identity

| Field | Value |
|-------|-------|
| **Repository** | [Cisco-Talos/clamav](https://github.com/Cisco-Talos/clamav) |
| **Organization** | Cisco-Talos (GitHub Org) |
| **Created** | July 20, 2012 |
| **Last Push** | May 14, 2026 |
| **Last Repo Update** | May 16, 2026 |
| **Stars** | 6,627 |
| **Forks** | 862 |
| **Watchers** | 135 |
| **Open Issues** | 339 |
| **Primary Language** | C |
| **Secondary Language** | Rust (libclamav_rust) |
| **License** | GNU General Public License v2.0 (GPL-2.0) |
| **Default Branch** | main |
| **Archived** | No |
| **Homepage** | https://www.clamav.net/ |

## Topics
`antivirus`, `clamav`, `gplv2`, `open-source`

## Description
ClamAV — Open source antivirus engine for detecting trojans, viruses, malware, and other malicious threats.

## Key Maintainers
- **Cisco Talos Intelligence Group**
- Originally by **Tomasz Kojm**

## Repository Health
- **Active maintenance**: Yes — industry-grade, backed by Cisco
- **Documentation**: https://docs.clamav.net
- **CI/CD**: Jenkins + GitHub Actions
- **Test coverage**: Comprehensive (`unit_tests/` directory)
- **Build system**: CMake with vcpkg dependency management
- **Cross-compilation**: Documented (ARM64 Linux, ARM64 Windows)

## Repository Structure (Key Directories)
- `libclamav/` — Core scanning engine (C, ~298 source files)
- `libclamav_rust/` — Rust components
- `libclammspack/` — CAB/MSPack decompression
- `libclamunrar/` — RAR decompression
- `libfreshclam/` — Signature update client
- `clamscan/` — CLI scanner
- `clamd/` — Daemon scanner
- `sigtool/` — Signature management tool

## License Warning

> [!WARNING]
> ClamAV is licensed under **GPL-2.0**. This is a copyleft license.
> Direct integration of ClamAV source code into Protctus would require
> the entire Protctus app to be GPL-2.0 licensed.
> For Protctus: the recommended approach is:
> - Compile libclamav as an isolated .so shared library
> - Interface via JNI as a separate process/library
> - OR: Implement our own signature matching engine inspired by ClamAV's algorithms

## Verification
- ✅ Official repository confirmed via GitHub org `Cisco-Talos`
- ✅ Active development (pushed May 2026)
- ✅ Industry-grade codebase backed by Cisco
- ✅ 1,438 files in repository
- ⚠️ GPL-2.0 license — requires legal consideration
- ⚠️ ~181MB repository size

## Clone Command Used
```bash
git clone --depth 1 https://github.com/Cisco-Talos/clamav.git research/clamav/source
```
