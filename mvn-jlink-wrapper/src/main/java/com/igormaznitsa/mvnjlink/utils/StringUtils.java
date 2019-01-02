package com.igormaznitsa.mvnjlink.utils;

import com.igormaznitsa.meta.annotation.MustNotContainNull;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.regex.Pattern.compile;

public final class StringUtils {
  private static final Pattern PATTERN_MODULE_LINE = compile("^(.*)->(.*)$");
  private static final Pattern PATTERN_FILE_HASH = compile("([0-9a-fA-F]+)\\s+(.+)");

  private StringUtils() {
  }

  @Nonnull
  public static String extractFileHash(@Nonnull final String text) throws IOException {
    final Matcher hashMatcher = PATTERN_FILE_HASH.matcher(text.trim());
    if (hashMatcher.find()) {
      return hashMatcher.group(1);
    } else {
      throw new IOException("Can't extract file hash from '" + text + '\'');
    }
  }

  public static int printTextProgress(@Nonnull final String text, final long value, final long maxValue, final int progressBarWidth, final int lastValue) {
    final StringBuilder builder = new StringBuilder();
    builder.append("\r\u001B[?25l");
    builder.append(text);
    builder.append("[");

    final int progress = max(0, min(progressBarWidth,(int)Math.round(progressBarWidth * ((double)value / (double)maxValue))));
    int nextValue = progress;

    for(int i=0;i<progress;i++) {
      builder.append('▒');
    }
    for(int i=progress;i<progressBarWidth;i++){
      builder.append('-');
    }
    builder.append("]\u001B[?25h");

    if (nextValue != lastValue) {
      System.out.print(builder.toString());
    }

    return nextValue;
  }

  @Nonnull
  @MustNotContainNull
  public static List<String> extractJdepsModuleNames(@Nonnull final String text) {
    final List<String> result = new ArrayList<>();
    for (final String line : text.split("\\n")) {
      final Matcher lineMatcher = PATTERN_MODULE_LINE.matcher(line);
      if (lineMatcher.find()) {
        final String moduleName = lineMatcher.group(2).trim();
        if (!moduleName.contains(" ")) {
          result.add(moduleName);
        }
      }
    }
    return result;
  }
}
