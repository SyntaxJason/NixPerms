package de.astranox.nixperms.lcs;

import de.tnttastisch.languages.LanguageModule;
import de.tnttastisch.languages.api.LanguageHandler;
import de.tnttastisch.languages.storage.database.common.model.LanguageModel;

public class LanguageConfigurationManager {

    private final LanguageModule languageModule;
    private LanguageHandler languageHandler;

    public LanguageConfigurationManager() {
        this.languageModule = new LanguageModule();

    }

    public void init() {
        languageModule.initLanguageModule();
        this.languageHandler = languageModule.getHandler();
    }

    public void dispose() {
        languageModule.disposeLanguageModule();
    }

    public void createLanguage(String languageId) {
        this.languageHandler.addLanguage(new LanguageModel(languageId));
    }
}
