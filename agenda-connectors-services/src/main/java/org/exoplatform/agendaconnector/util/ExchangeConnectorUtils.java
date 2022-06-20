package org.exoplatform.agendaconnector.util;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class ExchangeConnectorUtils {


    private static final Log LOG = ExoLogger.getLogger(ExchangeConnectorUtils.class);

    private ExchangeConnectorUtils() {
    }

    public static final String getCurrentUser() {
        return ConversationState.getCurrent().getIdentity().getUserId();
    }


    public static final long getCurrentUserIdentityId(IdentityManager identityManager) {
        String currentUser = getCurrentUser();
        Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
        return identity == null ? 0 : Long.parseLong(identity.getId());
    }

    public static String encode(String password) {
        try {
            CodecInitializer codecInitializer = CommonsUtils.getService(CodecInitializer.class);
            return codecInitializer.getCodec().encode(password);
        } catch (TokenServiceInitializationException e) {
            LOG.warn("Error when encoding password", e);
            return null;
        }
    }

    public static String decode(String password) {
        try {
            CodecInitializer codecInitializer = CommonsUtils.getService(CodecInitializer.class);
            return codecInitializer.getCodec().decode(password);
        } catch (TokenServiceInitializationException e) {
            LOG.warn("Error when decoding password", e);
            return null;
        }
    }
}
