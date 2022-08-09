import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ApplicationRef, DoBootstrap, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LanguageTranslationModule } from './shared/modules/language-translation/language-translation.module';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ApplicationsService } from './shared/services/applications.service';
import { EnvironmentsService } from './shared/services/environments.service';
import { ToastService } from './shared/modules/toast/toast.service';
import { TopicsService } from './shared/services/topics.service';

import { ServerInfoService } from './shared/services/serverinfo.service';
import { HIGHLIGHT_OPTIONS } from 'ngx-highlightjs';
import { getHighlightLanguages } from './layout/topics/topics.module';
import { ApiKeyService } from './shared/services/apikey.service';
import { CertificateService } from './shared/services/certificates.service';
import { AuthConfig, OAuthModule, OAuthService } from 'angular-oauth2-oidc';

const authConfig: AuthConfig = {
    issuer: 'http://localhost:8180/auth/realms/oidc-demo',
    redirectUri: window.location.origin,
    clientId: 'oidcdemo',
    responseType: 'code',
    strictDiscoveryDocumentValidation: false,
    scope: 'openid'
};

@NgModule({
    imports: [
        CommonModule,
        BrowserModule,
        BrowserAnimationsModule,
        HttpClientModule,
        LanguageTranslationModule,
        AppRoutingModule,
        OAuthModule.forRoot({
            resourceServer: {
                allowedUrls: ['http://localhost:8080'],
                sendAccessToken: true
            }
        })
    ],
    declarations: [AppComponent],
    providers: [ApplicationsService, EnvironmentsService, TopicsService, ApiKeyService, ToastService, CertificateService,
        ServerInfoService,
        {
            provide: HIGHLIGHT_OPTIONS,
            useValue: {
                coreLibraryLoader: () => import('highlight.js/lib/core'),
                languages: getHighlightLanguages()
            }
        }
    ]
})
export class AppModule implements DoBootstrap {
    constructor(private oauthService: OAuthService) {
    }

    ngDoBootstrap(app: ApplicationRef) {
        this.oauthService.configure(authConfig);
        this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
            if (!this.oauthService.hasValidAccessToken()) {
                this.oauthService.initLoginFlow();
            } else {
                this.oauthService.loadUserProfile().then(user => {
                    app.bootstrap(AppComponent);
                    console.log(JSON.stringify(user));
                });
            }
        });
    }
}
