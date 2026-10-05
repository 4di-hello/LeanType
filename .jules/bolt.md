## 2024-05-18 - Language Detector Optimization
**Learning:** `LanguageDetector.detect` uses `Regex("[^\\p{L}]+")` inside `detectByHeuristics`, creating a new Regex instance on every call. This method is called via `ProofreadHelper` which is likely triggered on user typing or interaction. Pre-compiling the Regex as a top-level constant avoids the overhead of regex compilation for every detection call.
**Action:** Always check for repeated Regex instantiations in frequently called string processing or detection methods and extract them to top-level or companion object properties.
