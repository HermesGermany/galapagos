package com.hermesworld.ais.galapagos.security.roles;

/**
 * Service interface for handling authorization logic in the application. Provides methods to verify user permissions
 * for various actions and resources.
 */
public interface AuthorizationService {

    /**
     * Checks if the current user has permission to view in the specified environment.
     *
     * @param environmentId The ID of the environment to check.
     * @return <code>true</code> if the user is authorized to view, otherwise <code>false</code>.
     */
    boolean canView(String environmentId, String topicName, String applicationId);

    /**
     * Checks if the current user has permission to edit in the specified environment.
     *
     * @param environmentId The ID of the environment to check.
     * @return <code>true</code> if the user is authorized to edit, otherwise <code>false</code>.
     */
    boolean canEdit(String environmentId);

    /**
     * Checks if the current user has permission to edit in the specified application.
     *
     * @param applicationId The ID of the application to check.
     * @return <code>true</code> if the user is authorized to edit, otherwise <code>false</code>.
     */
    boolean canEditApplication(String applicationId);

    /**
     * Checks if the current user has permission to generate a new API key in the specified application.
     *
     * @param applicationId The ID of the application.
     * @return <code>true</code> if the user is authorized to generate a new API key, otherwise <code>false</code>.
     */
    boolean canGenerateNewApiKey(String applicationId);
}
