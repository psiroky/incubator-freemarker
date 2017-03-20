/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.freemarker.core;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.freemarker.core.arithmetic.ArithmeticEngine;
import org.apache.freemarker.core.model.ObjectWrapper;
import org.apache.freemarker.core.outputformat.OutputFormat;
import org.apache.freemarker.core.templateresolver.impl.DefaultTemplateResolver;
import org.apache.freemarker.core.util._NullArgumentException;
import org.apache.freemarker.core.valueformat.TemplateDateFormatFactory;
import org.apache.freemarker.core.valueformat.TemplateNumberFormatFactory;

/**
 * Used for customizing the configuration settings for individual {@link Template}-s (or rather groups of templates),
 * relatively to the common setting values coming from the {@link Configuration}. This was designed with the standard
 * template loading mechanism of FreeMarker in mind ({@link Configuration#getTemplate(String)}
 * and {@link DefaultTemplateResolver}), though can also be reused for custom template loading and caching solutions.
 * 
 * <p>
 * Note on the {@code locale} setting: When used with the standard template loading/caching mechanism (
 * {@link Configuration#getTemplate(String)} and its overloads), localized lookup happens before the {@code locale}
 * specified here could have effect. The {@code locale} will be only set in the template that the localized lookup has
 * already found.
 * 
 * <p>
 * Note on the encoding setting {@code encoding}: See {@link #setEncoding(String)}.
 * 
 * <p>
 * Note that the result value of the reader methods (getter and "is" methods) is usually not useful unless the value of
 * that setting was already set on this object. Otherwise you will get the value from the parent {@link Configuration},
 * or an {@link IllegalStateException} before this object is associated to a {@link Configuration}.
 * 
 * <p>
 * If you are using this class for your own template loading and caching solution, rather than with the standard one,
 * you should be aware of a few more details:
 * 
 * <ul>
 * <li>This class implements both {@link Configurable} and {@link ParserConfiguration}. This means that it can influence
 * both the template parsing phase and the runtime settings. For both aspects (i.e., {@link ParserConfiguration} and
 * {@link Configurable}) to take effect, you have first pass this object to the {@link Template} constructor
 * (this is where the {@link ParserConfiguration} interface is used), and then you have to call {@link #apply(Template)}
 * on the resulting {@link Template} object (this is where the {@link Configurable} aspect is used).
 * 
 * <li>{@link #apply(Template)} only change the settings that weren't yet set on the {@link Template} (but are inherited
 * from the {@link Configuration}). This is primarily because if the template configures itself via the {@code #ftl}
 * header, those values should have precedence. A consequence of this is that if you want to configure the same
 * {@link Template} with multiple {@link TemplateConfiguration}-s, you either should merge them to a single one before
 * that (with {@link #merge(TemplateConfiguration)}), or you have to apply them in reverse order of their intended
 * precedence.
 * </ul>
 * 
 * @see Template#Template(String, String, Reader, Configuration, ParserConfiguration, String)
 * 
 * @since 2.3.24
 */
public final class TemplateConfiguration extends Configurable implements ParserConfiguration {

    private TemplateLanguage templateLanguage;
    private Integer tagSyntax;
    private Integer namingConvention;
    private Boolean whitespaceStripping;
    private Integer autoEscapingPolicy;
    private Boolean recognizeStandardFileExtensions;
    private OutputFormat outputFormat;
    private String encoding;
    private Integer tabSize;

    /**
     * Creates a new instance. The parent will be {@code null} initially, but it will
     * be changed to the real parent {@link Configuration} when this object is added to the {@link Configuration}. (It's
     * not allowed to add the same instance to multiple {@link Configuration}-s).
     */
    public TemplateConfiguration() {
        super((Configuration) null);
    }

    /**
     * Same as {@link #setParentConfiguration(Configuration)}.
     */
    @Override
    void setParent(Configurable cfg) {
        _NullArgumentException.check("cfg", cfg);
        if (!(cfg instanceof Configuration)) {
            throw new IllegalArgumentException("The parent of a TemplateConfiguration can only be a Configuration");
        }
        
        Configurable parent = getParent();
        if (parent != null) {
            if (parent != cfg) {
                throw new IllegalStateException(
                        "This TemplateConfiguration is already associated with a different Configuration instance.");
            }
            return;
        }
        
        super.setParent(cfg);
    }

    /**
     * Associates this instance with a {@link Configuration}; usually you don't call this, as it's called internally
     * when this instance is added to a {@link Configuration}. This method can be called only once (except with the same
     * {@link Configuration} parameter again, as that changes nothing anyway).
     * 
     * @throws IllegalArgumentException
     *             if the argument is {@code null} or not a {@link Configuration}
     * @throws IllegalStateException
     *             if this object is already associated to a different {@link Configuration} object,
     *             or if the {@code Configuration} has {@code #getIncompatibleImprovements()} less than 2.3.22 and
     *             this object tries to change any non-parser settings  
     */
    public void setParentConfiguration(Configuration cfg) {
        setParent(cfg);
    }

    /**
     * Returns the parent {@link Configuration}, or {@code null} if none was associated yet.
     */
    public Configuration getParentConfiguration() {
        return (Configuration) getParent();
    }

    private Configuration getNonNullParentConfiguration() {
        Configurable parent = getParent();
        if (parent == null) {
            throw new IllegalStateException("The TemplateConfiguration wasn't associated with a Configuration yet.");
        }
        return (Configuration) parent;
    }
    
    /**
     * Set all settings in this {@link TemplateConfiguration} that were set in the parameter
     * {@link TemplateConfiguration}, possibly overwriting the earlier value in this object. (A setting is said to be
     * set in a {@link TemplateConfiguration} if it was explicitly set via a setter method, as opposed to be inherited.)
     */
    public void merge(TemplateConfiguration tc) {
        if (tc.isAPIBuiltinEnabledSet()) {
            setAPIBuiltinEnabled(tc.isAPIBuiltinEnabled());
        }
        if (tc.isArithmeticEngineSet()) {
            setArithmeticEngine(tc.getArithmeticEngine());
        }
        if (tc.isAutoEscapingPolicySet()) {
            setAutoEscapingPolicy(tc.getAutoEscapingPolicy());
        }
        if (tc.isAutoFlushSet()) {
            setAutoFlush(tc.getAutoFlush());
        }
        if (tc.isBooleanFormatSet()) {
            setBooleanFormat(tc.getBooleanFormat());
        }
        if (tc.isCustomDateFormatsSet()) {
            setCustomDateFormats(mergeMaps(
                    isCustomDateFormatsSet() ? getCustomDateFormats() : null, tc.getCustomDateFormats(), false));
        }
        if (tc.isCustomNumberFormatsSet()) {
            setCustomNumberFormats(mergeMaps(
                    isCustomNumberFormatsSet() ? getCustomNumberFormats() : null, tc.getCustomNumberFormats(), false));
        }
        if (tc.isDateFormatSet()) {
            setDateFormat(tc.getDateFormat());
        }
        if (tc.isDateTimeFormatSet()) {
            setDateTimeFormat(tc.getDateTimeFormat());
        }
        if (tc.isEncodingSet()) {
            setEncoding(tc.getEncoding());
        }
        if (tc.isLocaleSet()) {
            setLocale(tc.getLocale());
        }
        if (tc.isLogTemplateExceptionsSet()) {
            setLogTemplateExceptions(tc.getLogTemplateExceptions());
        }
        if (tc.isNamingConventionSet()) {
            setNamingConvention(tc.getNamingConvention());
        }
        if (tc.isNewBuiltinClassResolverSet()) {
            setNewBuiltinClassResolver(tc.getNewBuiltinClassResolver());
        }
        if (tc.isNumberFormatSet()) {
            setNumberFormat(tc.getNumberFormat());
        }
        if (tc.isObjectWrapperSet()) {
            setObjectWrapper(tc.getObjectWrapper());
        }
        if (tc.isOutputEncodingSet()) {
            setOutputEncoding(tc.getOutputEncoding());
        }
        if (tc.isOutputFormatSet()) {
            setOutputFormat(tc.getOutputFormat());
        }
        if (tc.isRecognizeStandardFileExtensionsSet()) {
            setRecognizeStandardFileExtensions(tc.getRecognizeStandardFileExtensions());
        }
        if (tc.isShowErrorTipsSet()) {
            setShowErrorTips(tc.getShowErrorTips());
        }
        if (tc.isSQLDateAndTimeTimeZoneSet()) {
            setSQLDateAndTimeTimeZone(tc.getSQLDateAndTimeTimeZone());
        }
        if (tc.isTagSyntaxSet()) {
            setTagSyntax(tc.getTagSyntax());
        }
        if (tc.isTemplateLanguageSet()) {
            setTemplateLanguage(tc.getTemplateLanguage());
        }
        if (tc.isTemplateExceptionHandlerSet()) {
            setTemplateExceptionHandler(tc.getTemplateExceptionHandler());
        }
        if (tc.isTimeFormatSet()) {
            setTimeFormat(tc.getTimeFormat());
        }
        if (tc.isTimeZoneSet()) {
            setTimeZone(tc.getTimeZone());
        }
        if (tc.isURLEscapingCharsetSet()) {
            setURLEscapingCharset(tc.getURLEscapingCharset());
        }
        if (tc.isWhitespaceStrippingSet()) {
            setWhitespaceStripping(tc.getWhitespaceStripping());
        }
        if (tc.isTabSizeSet()) {
            setTabSize(tc.getTabSize());
        }
        if (tc.isLazyImportsSet()) {
            setLazyImports(tc.getLazyImports());
        }
        if (tc.isLazyAutoImportsSet()) {
            setLazyAutoImports(tc.getLazyAutoImports());
        }
        if (tc.isAutoImportsSet()) {
            setAutoImports(mergeMaps(getAutoImportsWithoutFallback(), tc.getAutoImportsWithoutFallback(),true));
        }
        if (tc.isAutoIncludesSet()) {
            setAutoIncludes(mergeLists(getAutoIncludesWithoutFallback(), tc.getAutoIncludesWithoutFallback()));
        }
        
        tc.copyDirectCustomAttributes(this, true);
    }

    /**
     * Sets those settings of the {@link Template} which aren't yet set in the {@link Template} and are set in this
     * {@link TemplateConfiguration}, leaves the other settings as is. A setting is said to be set in a
     * {@link TemplateConfiguration} or {@link Template} if it was explicitly set via a setter method on that object, as
     * opposed to be inherited from the {@link Configuration}.
     * 
     * <p>
     * Note that this method doesn't deal with settings that influence the parser, as those are already baked in at this
     * point via the {@link ParserConfiguration}. 
     * 
     * <p>
     * Note that the {@code encoding} setting of the {@link Template} counts as unset if it's {@code null},
     * even if {@code null} was set via {@link Template#setEncoding(String)}.
     *
     * @throws IllegalStateException
     *             If the parent configuration wasn't yet set.
     */
    public void apply(Template template) {
        Configuration cfg = getNonNullParentConfiguration();
        if (template.getConfiguration() != cfg) {
            // This is actually not a problem right now, but for future BC we enforce this.
            throw new IllegalArgumentException(
                    "The argument Template doesn't belong to the same Configuration as the TemplateConfiguration");
        }

        if (isAPIBuiltinEnabledSet() && !template.isAPIBuiltinEnabledSet()) {
            template.setAPIBuiltinEnabled(isAPIBuiltinEnabled());
        }
        if (isArithmeticEngineSet() && !template.isArithmeticEngineSet()) {
            template.setArithmeticEngine(getArithmeticEngine());
        }
        if (isAutoFlushSet() && !template.isAutoFlushSet()) {
            template.setAutoFlush(getAutoFlush());
        }
        if (isBooleanFormatSet() && !template.isBooleanFormatSet()) {
            template.setBooleanFormat(getBooleanFormat());
        }
        if (isCustomDateFormatsSet()) {
            template.setCustomDateFormats(
                    mergeMaps(getCustomDateFormats(), template.getCustomDateFormatsWithoutFallback(), false));
        }
        if (isCustomNumberFormatsSet()) {
            template.setCustomNumberFormats(
                    mergeMaps(getCustomNumberFormats(), template.getCustomNumberFormatsWithoutFallback(), false));
        }
        if (isDateFormatSet() && !template.isDateFormatSet()) {
            template.setDateFormat(getDateFormat());
        }
        if (isDateTimeFormatSet() && !template.isDateTimeFormatSet()) {
            template.setDateTimeFormat(getDateTimeFormat());
        }
        if (isEncodingSet() && template.getEncoding() == null) {
            template.setEncoding(getEncoding());
        }
        if (isLocaleSet() && !template.isLocaleSet()) {
            template.setLocale(getLocale());
        }
        if (isLogTemplateExceptionsSet() && !template.isLogTemplateExceptionsSet()) {
            template.setLogTemplateExceptions(getLogTemplateExceptions());
        }
        if (isNewBuiltinClassResolverSet() && !template.isNewBuiltinClassResolverSet()) {
            template.setNewBuiltinClassResolver(getNewBuiltinClassResolver());
        }
        if (isNumberFormatSet() && !template.isNumberFormatSet()) {
            template.setNumberFormat(getNumberFormat());
        }
        if (isObjectWrapperSet() && !template.isObjectWrapperSet()) {
            template.setObjectWrapper(getObjectWrapper());
        }
        if (isOutputEncodingSet() && !template.isOutputEncodingSet()) {
            template.setOutputEncoding(getOutputEncoding());
        }
        if (isShowErrorTipsSet() && !template.isShowErrorTipsSet()) {
            template.setShowErrorTips(getShowErrorTips());
        }
        if (isSQLDateAndTimeTimeZoneSet() && !template.isSQLDateAndTimeTimeZoneSet()) {
            template.setSQLDateAndTimeTimeZone(getSQLDateAndTimeTimeZone());
        }
        if (isTemplateExceptionHandlerSet() && !template.isTemplateExceptionHandlerSet()) {
            template.setTemplateExceptionHandler(getTemplateExceptionHandler());
        }
        if (isTimeFormatSet() && !template.isTimeFormatSet()) {
            template.setTimeFormat(getTimeFormat());
        }
        if (isTimeZoneSet() && !template.isTimeZoneSet()) {
            template.setTimeZone(getTimeZone());
        }
        if (isURLEscapingCharsetSet() && !template.isURLEscapingCharsetSet()) {
            template.setURLEscapingCharset(getURLEscapingCharset());
        }
        if (isLazyImportsSet() && !template.isLazyImportsSet()) {
            template.setLazyImports(getLazyImports());
        }
        if (isLazyAutoImportsSet() && !template.isLazyAutoImportsSet()) {
            template.setLazyAutoImports(getLazyAutoImports());
        }
        if (isAutoImportsSet()) {
            // Regarding the order of the maps in the merge:
            // - Existing template-level imports have precedence over those coming from the TC (just as with the others
            //   apply()-ed settings), thus for clashing import prefixes they must win.
            // - Template-level imports count as more specific, and so come after the more generic ones from TC.
            template.setAutoImports(mergeMaps(getAutoImports(), template.getAutoImportsWithoutFallback(), true));
        }
        if (isAutoIncludesSet()) {
            template.setAutoIncludes(mergeLists(getAutoIncludes(), template.getAutoIncludesWithoutFallback()));
        }
        
        copyDirectCustomAttributes(template, false);
    }

    /**
     * See {@link Configuration#setTagSyntax(int)}.
     */
    public void setTagSyntax(int tagSyntax) {
        Configuration.valideTagSyntaxValue(tagSyntax);
        this.tagSyntax = tagSyntax;
    }

    /**
     * The getter pair of {@link #setTagSyntax(int)}.
     */
    @Override
    public int getTagSyntax() {
        return tagSyntax != null ? tagSyntax : getNonNullParentConfiguration().getTagSyntax();
    }

    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isTagSyntaxSet() {
        return tagSyntax != null;
    }

    /**
     * See {@link Configuration#getTemplateLanguage()}
     */
    @Override
    public TemplateLanguage getTemplateLanguage() {
        return templateLanguage != null ? templateLanguage : getNonNullParentConfiguration().getTemplateLanguage();
    }

    /**
     * See {@link Configuration#setTemplateLanguage(TemplateLanguage)}
     */
    public void setTemplateLanguage(TemplateLanguage templateLanguage) {
        _NullArgumentException.check("templateLanguage", templateLanguage);
        this.templateLanguage = templateLanguage;
    }

    public boolean isTemplateLanguageSet() {
        return templateLanguage != null;
    }

    /**
     * See {@link Configuration#setNamingConvention(int)}.
     */
    public void setNamingConvention(int namingConvention) {
        Configuration.validateNamingConventionValue(namingConvention);
        this.namingConvention = namingConvention;
    }

    /**
     * The getter pair of {@link #setNamingConvention(int)}.
     */
    @Override
    public int getNamingConvention() {
        return namingConvention != null ? namingConvention
                : getNonNullParentConfiguration().getNamingConvention();
    }

    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isNamingConventionSet() {
        return namingConvention != null;
    }

    /**
     * See {@link Configuration#setWhitespaceStripping(boolean)}.
     */
    public void setWhitespaceStripping(boolean whitespaceStripping) {
        this.whitespaceStripping = Boolean.valueOf(whitespaceStripping);
    }

    /**
     * The getter pair of {@link #getWhitespaceStripping()}.
     */
    @Override
    public boolean getWhitespaceStripping() {
        return whitespaceStripping != null ? whitespaceStripping.booleanValue()
                : getNonNullParentConfiguration().getWhitespaceStripping();
    }

    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isWhitespaceStrippingSet() {
        return whitespaceStripping != null;
    }

    /**
     * Sets the output format of the template; see {@link Configuration#setAutoEscapingPolicy(int)} for more.
     */
    public void setAutoEscapingPolicy(int autoEscapingPolicy) {
        Configuration.validateAutoEscapingPolicyValue(autoEscapingPolicy);

        this.autoEscapingPolicy = Integer.valueOf(autoEscapingPolicy);
    }

    /**
     * The getter pair of {@link #setAutoEscapingPolicy(int)}.
     */
    @Override
    public int getAutoEscapingPolicy() {
        return autoEscapingPolicy != null ? autoEscapingPolicy.intValue()
                : getNonNullParentConfiguration().getAutoEscapingPolicy();
    }

    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isAutoEscapingPolicySet() {
        return autoEscapingPolicy != null;
    }

    /**
     * Sets the output format of the template; see {@link Configuration#setOutputFormat(OutputFormat)} for more.
     */
    public void setOutputFormat(OutputFormat outputFormat) {
        _NullArgumentException.check("outputFormat", outputFormat);
        this.outputFormat = outputFormat;
    }

    /**
     * The getter pair of {@link #setOutputFormat(OutputFormat)}.
     */
    @Override
    public OutputFormat getOutputFormat() {
        return outputFormat != null ? outputFormat : getNonNullParentConfiguration().getOutputFormat();
    }

    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isOutputFormatSet() {
        return outputFormat != null;
    }
    
    /**
     * See {@link Configuration#setRecognizeStandardFileExtensions(boolean)}. 
     */
    public void setRecognizeStandardFileExtensions(boolean recognizeStandardFileExtensions) {
        this.recognizeStandardFileExtensions = Boolean.valueOf(recognizeStandardFileExtensions);
    }

    /**
     * Getter pair of {@link #setRecognizeStandardFileExtensions(boolean)}.
     */
    @Override
    public boolean getRecognizeStandardFileExtensions() {
        return recognizeStandardFileExtensions != null ? recognizeStandardFileExtensions.booleanValue()
                : getNonNullParentConfiguration().getRecognizeStandardFileExtensions();
    }
    
    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     */
    public boolean isRecognizeStandardFileExtensionsSet() {
        return recognizeStandardFileExtensions != null;
    }

    public String getEncoding() {
        return encoding != null ? encoding : getNonNullParentConfiguration().getDefaultEncoding();
    }

    /**
     * When the standard template loading/caching mechanism is used, this forces the charset used for reading the
     * template "file", overriding everything but the encoding coming from the {@code #ftl} header.
     * 
     * <p>
     * If you are developing your own template loading/caching mechanism instead of the standard one, note that the
     * above behavior is not guaranteed by this class alone; you have to ensure it. Also, read the note on
     * {@code encoding} in the documentation of {@link #apply(Template)}.
     */
    public void setEncoding(String encoding) {
        _NullArgumentException.check("encoding", encoding);
        this.encoding = encoding;
    }

    public boolean isEncodingSet() {
        return encoding != null;
    }
    
    /**
     * See {@link Configuration#setTabSize(int)}. 
     * 
     * @since 2.3.25
     */
    public void setTabSize(int tabSize) {
        this.tabSize = Integer.valueOf(tabSize);
    }

    /**
     * Getter pair of {@link #setTabSize(int)}.
     * 
     * @since 2.3.25
     */
    @Override
    public int getTabSize() {
        return tabSize != null ? tabSize.intValue()
                : getNonNullParentConfiguration().getTabSize();
    }
    
    /**
     * Tells if this setting is set directly in this object or its value is coming from the {@link #getParent() parent}.
     * 
     * @since 2.3.25
     */
    public boolean isTabSizeSet() {
        return tabSize != null;
    }
    
    /**
     * Returns {@link Configuration#getIncompatibleImprovements()} from the parent {@link Configuration}. This mostly
     * just exist to satisfy the {@link ParserConfiguration} interface.
     * 
     * @throws IllegalStateException
     *             If the parent configuration wasn't yet set.
     */
    @Override
    public Version getIncompatibleImprovements() {
        return getNonNullParentConfiguration().getIncompatibleImprovements();
    }
    
    

    @Override
    public Locale getLocale() {
        try {
            return super.getLocale();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TimeZone getTimeZone() {
        try {
            return super.getTimeZone();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TimeZone getSQLDateAndTimeTimeZone() {
        try {
            return super.getSQLDateAndTimeTimeZone();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getNumberFormat() {
        try {
            return super.getNumberFormat();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public Map<String, ? extends TemplateNumberFormatFactory> getCustomNumberFormats() {
        try {
            return super.getCustomNumberFormats();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TemplateNumberFormatFactory getCustomNumberFormat(String name) {
        try {
            return super.getCustomNumberFormat(name);
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean hasCustomFormats() {
        try {
            return super.hasCustomFormats();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getBooleanFormat() {
        try {
            return super.getBooleanFormat();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getTimeFormat() {
        try {
            return super.getTimeFormat();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getDateFormat() {
        try {
            return super.getDateFormat();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getDateTimeFormat() {
        try {
            return super.getDateTimeFormat();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public Map<String, ? extends TemplateDateFormatFactory> getCustomDateFormats() {
        try {
            return super.getCustomDateFormats();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TemplateDateFormatFactory getCustomDateFormat(String name) {
        try {
            return super.getCustomDateFormat(name);
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TemplateExceptionHandler getTemplateExceptionHandler() {
        try {
            return super.getTemplateExceptionHandler();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public ArithmeticEngine getArithmeticEngine() {
        try {
            return super.getArithmeticEngine();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public ObjectWrapper getObjectWrapper() {
        try {
            return super.getObjectWrapper();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getOutputEncoding() {
        try {
            return super.getOutputEncoding();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String getURLEscapingCharset() {
        try {
            return super.getURLEscapingCharset();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public TemplateClassResolver getNewBuiltinClassResolver() {
        try {
            return super.getNewBuiltinClassResolver();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean getAutoFlush() {
        try {
            return super.getAutoFlush();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean getShowErrorTips() {
        try {
            return super.getShowErrorTips();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean isAPIBuiltinEnabled() {
        try {
            return super.isAPIBuiltinEnabled();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean getLogTemplateExceptions() {
        try {
            return super.getLogTemplateExceptions();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public boolean getLazyImports() {
        try {
            return super.getLazyImports();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public Boolean getLazyAutoImports() {
        try {
            return super.getLazyAutoImports();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public Map<String, String> getAutoImports() {
        try {
            return super.getAutoImports();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public List<String> getAutoIncludes() {
        try {
            return super.getAutoIncludes();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public String[] getCustomAttributeNames() {
        try {
            return super.getCustomAttributeNames();
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    @Override
    public Object getCustomAttribute(String name) {
        try {
            return super.getCustomAttribute(name);
        } catch (NullPointerException e) {
            getNonNullParentConfiguration();
            throw e;
        }
    }

    private boolean hasAnyConfigurableSet() {
        return
                isAPIBuiltinEnabledSet()
                || isArithmeticEngineSet()
                || isAutoFlushSet()
                || isAutoImportsSet()
                || isAutoIncludesSet()
                || isBooleanFormatSet()
                || isCustomDateFormatsSet()
                || isCustomNumberFormatsSet()
                || isDateFormatSet()
                || isDateTimeFormatSet()
                || isLazyImportsSet()
                || isLazyAutoImportsSet()
                || isLocaleSet()
                || isLogTemplateExceptionsSet()
                || isNewBuiltinClassResolverSet()
                || isNumberFormatSet()
                || isObjectWrapperSet()
                || isOutputEncodingSet()
                || isShowErrorTipsSet()
                || isSQLDateAndTimeTimeZoneSet()
                || isTemplateExceptionHandlerSet()
                || isTimeFormatSet()
                || isTimeZoneSet()
                || isURLEscapingCharsetSet();
    }
    
    private Map mergeMaps(Map m1, Map m2, boolean overwriteUpdatesOrder) {
        if (m1 == null) return m2;
        if (m2 == null) return m1;
        if (m1.isEmpty()) return m2 != null ? m2 : m1;
        if (m2.isEmpty()) return m1 != null ? m1 : m2;
        
        LinkedHashMap mergedM = new LinkedHashMap((m1.size() + m2.size()) * 4 / 3 + 1, 0.75f);
        mergedM.putAll(m1);
        for (Object m2Key : m2.keySet()) {
            mergedM.remove(m2Key); // So that duplicate keys are moved after m1 keys
        }
        mergedM.putAll(m2);
        return mergedM;
    }

    private List<String> mergeLists(List<String> list1, List<String> list2) {
        if (list1 == null) return list2;
        if (list2 == null) return list1;
        if (list1.isEmpty()) return list2 != null ? list2 : list1;
        if (list2.isEmpty()) return list1 != null ? list1 : list2;
        
        ArrayList<String> mergedList = new ArrayList<>(list1.size() + list2.size());
        mergedList.addAll(list1);
        mergedList.addAll(list2);
        return mergedList;
    }
    
}