import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ApplicationRef, DoBootstrap, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LanguageTranslationModule } from './shared/modules/language-translation/language-translation.module';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AuthGuard } from './shared';

import { ApplicationsService } from './shared/services/applications.service';
import { EnvironmentsService } from './shared/services/environments.service';
import { ToastService } from './shared/modules/toast/toast.service';
import { TopicsService } from './shared/services/topics.service';

import { ServerInfoService } from './shared/services/serverinfo.service';
import { HIGHLIGHT_OPTIONS } from 'ngx-highlightjs';
import { getHighlightLanguages } from './layout/topics/topics.module';
import { ApiKeyService } from './shared/services/apikey.service';
import { CertificateService } from './shared/services/certificates.service';

import { OAuthModule, OAuthService } from 'angular-oauth2-oidc';

const isApiUrl = (url: string) => !url.startsWith('http') && url.indexOf('/api/') > -1;

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
                sendAccessToken: true,
                customUrlValidation: isApiUrl
            }
        })
    ],
    declarations: [AppComponent],
    providers: [AuthGuard, ApplicationsService, EnvironmentsService, TopicsService, ApiKeyService, ToastService, CertificateService,
        ServerInfoService, OAuthService,
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

    ngDoBootstrap(app: ApplicationRef) {
        app.bootstrap(AppComponent);
    }
}
