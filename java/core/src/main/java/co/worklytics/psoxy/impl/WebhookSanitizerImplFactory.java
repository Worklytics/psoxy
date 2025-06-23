package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.WebhookCollectionRules;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface WebhookSanitizerImplFactory {

    WebhookSanitizerImpl create(@Assisted WebhookCollectionRules webhookCollectionRules);
}
