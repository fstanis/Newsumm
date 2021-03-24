# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from sumy.parsers.html import HtmlParser
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.lsa import LsaSummarizer
from sumy.nlp.stemmers import Stemmer
from sumy.utils import get_stop_words
import nltk

def _summarize(parser, language, sentences_count):
    stemmer = Stemmer(language)
    summarizer = LsaSummarizer(stemmer)
    summarizer.stop_words = get_stop_words(language)
    sentences = summarizer(parser.document, sentences_count)
    return [str(s) for s in sentences]

def summarize_html(input, language, sentences_count):
    return _summarize(
        HtmlParser.from_string(input, None, Tokenizer(language)),
        language,
        sentences_count
    )

def summarize_text(input, language, sentences_count):
    return _summarize(
        PlaintextParser.from_string(input, Tokenizer(language)),
        language,
        sentences_count
    )

def download_punkt():
    return nltk.download('punkt', quiet=True, raise_on_error=True)
