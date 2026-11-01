"""Python port of LexicalFeatureExtractor (Kotlin) for offline model evaluation.

Replicates the exact feature engineering pipeline used by the NeuroShield ONNX model.
Feature order MUST match the training pipeline / Kotlin extractor.
"""
import json
import math


class LexicalFeatureExtractor:
    FEATURE_COUNT = 53
    MAX_DOMAIN_LENGTH = 2048
    LN2 = 0.6931471805599453

    COUNTRY_TLDS = {
        "ac","ad","ae","af","ag","ai","al","am","ao","aq","ar","as","at","au","aw","ax","az",
        "ba","bb","bd","be","bf","bg","bh","bi","bj","bl","bm","bn","bo","bq","br","bs","bt",
        "bv","bw","by","bz","ca","cc","cd","cf","cg","ch","ci","ck","cl","cm","cn","co","cr",
        "cu","cv","cw","cx","cy","cz","de","dj","dk","dm","do","dz","ec","ee","eg","eh","er",
        "es","et","eu","fi","fj","fk","fm","fo","fr","ga","gb","gd","ge","gf","gg","gh","gi",
        "gl","gm","gn","gp","gq","gr","gs","gt","gu","gw","gy","hk","hm","hn","hr","ht","hu",
        "id","ie","il","im","in","io","iq","ir","is","it","je","jm","jo","jp","ke","kg","kh",
        "ki","km","kn","kp","kr","kw","ky","kz","la","lb","lc","li","lk","lr","ls","lt","lu",
        "lv","ly","ma","mc","md","me","mf","mg","mh","mk","ml","mm","mn","mo","mp","mq","mr",
        "ms","mt","mu","mv","mw","mx","my","mz","na","nc","ne","nf","ng","ni","nl","no","np",
        "nr","nu","nz","om","pa","pe","pf","pg","ph","pk","pl","pm","pn","pr","ps","pt","pw",
        "py","qa","re","ro","rs","ru","rw","sa","sb","sc","sd","se","sg","sh","si","sj","sk",
        "sl","sm","sn","so","sr","ss","st","sv","sx","sy","sz","tc","td","tf","tg","th","tj",
        "tk","tl","tm","tn","to","tr","tt","tv","tw","tz","ua","ug","uk","us","uy","uz","va",
        "vc","ve","vg","vi","vn","vu","wf","ws","ye","yt","za","zm","zw",
    }

    FREE_HOSTING_SUFFIXES = {
        "blogspot.com", "wordpress.com", "weebly.com", "wixsite.com",
        "000webhostapp.com", "infinityfreeapp.com", "rf.gd",
        "herokuapp.com", "vercel.app", "netlify.app", "pages.dev",
        "web.app", "firebaseapp.com", "appspot.com",
        "github.io", "gitlab.io", "surge.sh", "glitch.me",
        "replit.app", "repl.co", "onrender.com", "railway.app",
        "fly.dev", "deno.dev", "workers.dev", "r2.dev",
        "azurewebsites.net", "azurestaticapps.net", "cloudfront.net",
        "amplifyapp.com", "notion.site", "webflow.io", "carrd.co",
        "framer.app", "strikingly.com", "sites.google.com",
    }

    VOWELS = set("aeiou")
    RARE_CHARS = set('_~!$&\'()*+,;=')

    def __init__(self, dynamic_data_path: str | None = None):
        # Dynamic corpus statistics (features 46-52). Loaded from the same
        # bundled asset used in production (neuroshield_dynamic_data.json) so
        # that evaluation preprocessing matches production preprocessing.
        self.tld_phishing_rates: dict[str, float] = {}
        self.brands: set[str] = set()
        self.suspicious_tlds: set[str] = set()
        self.new_gtlds: set[str] = set()
        self.keywords: set[str] = set()
        self.dynamic_loaded = False
        if dynamic_data_path:
            self._load_dynamic_data(dynamic_data_path)

    def _load_dynamic_data(self, path: str) -> None:
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
            self.tld_phishing_rates = {
                str(k): float(v) for k, v in data.get("tld_phishing_rates", {}).items()
            }
            self.brands = set(data.get("brands", []))
            self.suspicious_tlds = set(data.get("suspicious_tlds", []))
            self.new_gtlds = set(data.get("new_gtlds", []))
            self.keywords = set(data.get("keywords", []))
            self.dynamic_loaded = True
        except Exception:
            # Leave dynamic features zeroed if the asset is unavailable.
            self.dynamic_loaded = False

    def extract(self, domain: str):
        features = [0.0] * self.FEATURE_COUNT
        clean = self.sanitize(domain)
        if not clean:
            return features

        parts = clean.split('.')
        tld = parts[-1] if len(parts) >= 2 else ""
        sld = parts[-2] if len(parts) >= 2 else clean
        subdomain_parts = parts[:-2] if len(parts) >= 3 else []
        subdomain = '.'.join(subdomain_parts)
        domain_depth = len(parts)

        idx = 0
        features[idx] = float(len(clean)); idx += 1                      # domain_length
        features[idx] = float(max(0, domain_depth - 2)); idx += 1         # subdomain_count
        features[idx] = self.shannon_entropy(clean); idx += 1            # domain_entropy
        features[idx] = self.digit_ratio(clean); idx += 1                # digit_ratio
        features[idx] = self.alpha_ratio(clean); idx += 1                # alpha_ratio
        features[idx] = self.vowel_ratio(clean); idx += 1                # vowel_ratio
        features[idx] = float(self.count_digits(clean)); idx += 1        # num_digits
        features[idx] = float(clean.count('.')); idx += 1                # num_dots
        features[idx] = float(clean.count('-')); idx += 1                # num_hyphens
        features[idx] = float(len(tld)); idx += 1                        # tld_length
        features[idx] = float(self.max_consonant_run(clean)); idx += 1   # max_consonant_run
        features[idx] = 1.0 if 'xn--' in clean else 0.0; idx += 1        # has_punycode
        features[idx] = 1.0 if self.is_raw_ip(clean) else 0.0; idx += 1  # is_raw_ip
        features[idx] = self.consonant_ratio(clean); idx += 1            # consonant_ratio
        features[idx] = float(self.count_consonants(clean)); idx += 1    # consonant_count
        features[idx] = float(self.count_vowels(clean)); idx += 1        # vowel_count

        subdomain_lengths = [len(p) for p in subdomain_parts]
        features[idx] = float(max(subdomain_lengths) if subdomain_lengths else 0); idx += 1  # longest_subdomain_len
        features[idx] = float(sum(subdomain_lengths) / len(subdomain_lengths)) if subdomain_lengths else 0.0; idx += 1  # average_subdomain_len

        features[idx] = float(len(sld)); idx += 1                        # sld_length
        features[idx] = self.shannon_entropy(sld); idx += 1              # sld_entropy
        features[idx] = self.digit_ratio(sld); idx += 1                  # sld_digit_ratio
        features[idx] = float(sld.count('-')); idx += 1                  # sld_hyphen_count

        features[idx] = (float(len(subdomain)) / float(len(clean))) if clean else 0.0; idx += 1  # subdomain_length_ratio
        features[idx] = 1.0 if self.is_free_hosting(clean) else 0.0; idx += 1  # is_free_hosting
        features[idx] = 1.0 if tld.lower() in self.COUNTRY_TLDS else 0.0; idx += 1  # is_country_tld
        features[idx] = (float(clean.count('.')) / float(len(clean))) if clean else 0.0; idx += 1  # dot_ratio
        features[idx] = (float(clean.count('-')) / float(len(clean))) if clean else 0.0; idx += 1  # hyphen_ratio
        features[idx] = 1.0 if '--' in clean else 0.0; idx += 1          # has_double_hyphen
        features[idx] = 1.0 if self.count_digits(clean) > 0 else 0.0; idx += 1  # has_numbers
        features[idx] = 1.0 if subdomain_parts else 0.0; idx += 1        # has_subdomains

        features[idx] = float(self.count_vowels(sld)); idx += 1          # sld_vowel_count
        features[idx] = float(self.count_consonants(sld)); idx += 1      # sld_consonant_count
        features[idx] = 1.0 if (sld and sld[0].isdigit()) else 0.0; idx += 1  # sld_starts_with_digit
        features[idx] = self.dga_score(sld); idx += 1                    # sld_dga_score
        features[idx] = float(self.count_digits(sld)); idx += 1          # sld_digit_count
        features[idx] = float(self.count_rare_chars(sld)); idx += 1      # sld_rare_char_count
        features[idx] = float(self.count_digit_sequences(sld)); idx += 1  # sld_digit_seq_count
        features[idx] = float(self.max_consonant_run(sld)); idx += 1     # sld_max_consonant_run

        features[idx] = self.digit_ratio(subdomain); idx += 1            # subdomain_digit_ratio
        features[idx] = float(len(subdomain_parts)); idx += 1            # subdomain_token_count
        features[idx] = float(subdomain.count('-')); idx += 1            # subdomain_hyphen_count
        features[idx] = self.shannon_entropy(subdomain); idx += 1        # subdomain_entropy
        se_sld = self.shannon_entropy(sld)
        features[idx] = (self.shannon_entropy(subdomain) / se_sld) if se_sld > 0 else 0.0; idx += 1  # entropy_ratio

        features[idx] = self.subdomain_sld_similarity(subdomain, sld); idx += 1  # subdomain_sld_similarity
        features[idx] = self.char_transition_variance(clean); idx += 1   # char_transition_variance
        features[idx] = float(domain_depth); idx += 1                     # domain_depth

        # Dynamic features (indices 46..52). Production populates these from the
        # bundled neuroshield_dynamic_data.json corpus statistics; mirror that here
        # so evaluation preprocessing matches production preprocessing. When the
        # dynamic asset is unavailable, they remain zeroed (safe fallback).
        if self.dynamic_loaded:
            sld_l = sld.lower()
            subdomain_l = subdomain.lower()
            tld_l = tld.lower()
            has_brand_in_sld = 1.0 if any(b in sld_l for b in self.brands) else 0.0
            features[idx] = has_brand_in_sld; idx += 1                                   # 46 has_brand_in_sld
            features[idx] = 1.0 if (has_brand_in_sld > 0.5 and tld_l in self.suspicious_tlds) else 0.0; idx += 1  # 47 brand_in_bad_tld
            features[idx] = 1.0 if tld_l in self.new_gtlds else 0.0; idx += 1             # 48 is_new_gtld
            features[idx] = self.tld_phishing_rates.get(tld_l, 0.0); idx += 1             # 49 tld_phishing_rate
            features[idx] = 1.0 if any(k in sld_l for k in self.keywords) else 0.0; idx += 1  # 50 sld_has_keyword
            features[idx] = 1.0 if any(b in subdomain_l for b in self.brands) else 0.0; idx += 1  # 51 subdomain_has_brand
            features[idx] = 1.0 if any(k in subdomain_l for k in self.keywords) else 0.0; idx += 1  # 52 subdomain_has_keyword
        else:
            for _ in range(7):
                features[idx] = 0.0; idx += 1

        assert idx == self.FEATURE_COUNT, f"feature index {idx} != {self.FEATURE_COUNT}"
        return features

    # ---------------- helpers ----------------
    def sanitize(self, domain: str) -> str:
        d = domain.strip().lower()
        if len(d) > self.MAX_DOMAIN_LENGTH:
            d = d[:self.MAX_DOMAIN_LENGTH]
        for prefix in ("http://", "https://"):
            if d.startswith(prefix):
                d = d[len(prefix):]
                break
        if d.startswith("www."):
            d = d[4:]
        for sep in ("/", "?", "#"):
            i = d.find(sep)
            if i >= 0:
                d = d[:i]
        i = d.rfind(':')
        if i >= 0:
            after = d[i + 1:]
            if after and all(ch.isdigit() for ch in after):
                d = d[:i]
        d = d.rstrip('.')
        try:
            import idna
            d = idna.encode(d).decode('ascii').lower()
        except Exception:
            try:
                d = d.encode('idna').decode('ascii').lower()
            except Exception:
                pass
        return d

    def shannon_entropy(self, s: str) -> float:
        if not s:
            return 0.0
        length = float(len(s))
        counts = [0] * 128
        non_ascii = 0
        for ch in s:
            code = ord(ch)
            if code < 128:
                counts[code] += 1
            else:
                non_ascii += 1
        entropy = 0.0
        for count in counts:
            if count > 0:
                p = count / length
                entropy -= p * (math.log(p) / self.LN2)
        if non_ascii > 0:
            p = non_ascii / length
            entropy -= p * (math.log(p) / self.LN2)
        return entropy

    def count_digits(self, s): return sum(1 for ch in s if ch.isdigit())
    def count_vowels(self, s): return sum(1 for ch in s if ch.lower() in self.VOWELS)
    def count_consonants(self, s):
        return sum(1 for ch in s if ch.lower().isalpha() and ch.lower() not in self.VOWELS)
    def count_rare_chars(self, s): return sum(1 for ch in s if ch in self.RARE_CHARS)
    def digit_ratio(self, s):
        if not s: return 0.0
        return self.count_digits(s) / len(s)
    def alpha_ratio(self, s):
        if not s: return 0.0
        return sum(1 for ch in s if ch.isalpha()) / len(s)
    def vowel_ratio(self, s):
        if not s: return 0.0
        return self.count_vowels(s) / len(s)
    def consonant_ratio(self, s):
        if not s: return 0.0
        return self.count_consonants(s) / len(s)
    def max_consonant_run(self, s):
        max_run = cur = 0
        for ch in s:
            lc = ch.lower()
            if lc.isalpha() and lc not in self.VOWELS:
                cur += 1
                max_run = max(max_run, cur)
            else:
                cur = 0
        return max_run
    def is_raw_ip(self, domain: str) -> bool:
        parts = domain.split('.')
        if len(parts) == 4:
            ok = all(
                1 <= len(p) <= 3 and p.isdigit() and 0 <= int(p) <= 255
                for p in parts
            )
            if ok:
                return True
        if ':' in domain and all(ch == ':' or ch.isdigit() or ch in 'abcdef' for ch in domain):
            return True
        return False
    def is_free_hosting(self, domain: str) -> bool:
        d = domain.lower()
        return any(d == s or d.endswith('.' + s) for s in self.FREE_HOSTING_SUFFIXES)
    def count_digit_sequences(self, s):
        count = 0
        in_seq = False
        for ch in s:
            if ch.isdigit():
                if not in_seq:
                    count += 1
                    in_seq = True
            else:
                in_seq = False
        return count
    def dga_score(self, s: str) -> float:
        if len(s) < 3:
            return 0.0
        transitions = 0
        prev_is_vowel = None
        for ch in s:
            if not ch.isalpha():
                continue
            is_vowel = ch.lower() in self.VOWELS
            if prev_is_vowel is not None and is_vowel != prev_is_vowel:
                transitions += 1
            prev_is_vowel = is_vowel
        letter_count = sum(1 for ch in s if ch.isalpha())
        if letter_count < 2:
            return 0.0
        transition_rate = transitions / float(letter_count - 1)
        entropy = self.shannon_entropy(s)
        return (1.0 - transition_rate) * 0.5 + min(entropy / 5.0, 1.0) * 0.5
    def subdomain_sld_similarity(self, subdomain: str, sld: str) -> float:
        if not subdomain or not sld:
            return 0.0
        set_a = set(subdomain)
        set_b = set(sld)
        inter = len(set_a & set_b)
        union = len(set_a | set_b)
        return inter / union if union > 0 else 0.0
    def char_transition_variance(self, s: str) -> float:
        if len(s) < 2:
            return 0.0
        transitions = [float(ord(s[i + 1]) - ord(s[i])) for i in range(len(s) - 1)]
        mean = sum(transitions) / len(transitions)
        var = sum((t - mean) ** 2 for t in transitions) / len(transitions)
        return var
