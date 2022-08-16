import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
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
import { OAuthModule } from 'angular-oauth2-oidc';
import { OidcService } from './shared/services/oidc.service';
import { AuthGuard } from './shared/guard';
import { MyDefaultOAuthInterceptor } from './shared/oauth-interceptor';


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
                allowedUrls: ['http://localhost:8080/api', 'http://localhost:4200/api', '/api'],
                sendAccessToken: true
            }
        })
    ],
    declarations: [AppComponent],
    providers: [AuthGuard, ApplicationsService, EnvironmentsService, TopicsService, ApiKeyService, ToastService, CertificateService,
        ServerInfoService,
        {
            provide: HIGHLIGHT_OPTIONS,
            useValue: {
                coreLibraryLoader: () => import('highlight.js/lib/core'),
                languages: getHighlightLanguages()
            }
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: MyDefaultOAuthInterceptor,
            multi: true
        }
    ]
})
export class AppModule implements DoBootstrap {
    constructor(private oidcService: OidcService) {
    }

    ngDoBootstrap(app: ApplicationRef) {
        this.oidcService.loadUser().then(() => app.bootstrap(AppComponent))
            .catch(err => console.log('Could not get user: ' + err));
    }
}
