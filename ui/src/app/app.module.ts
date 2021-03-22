import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ApplicationRef, DoBootstrap, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LanguageTranslationModule } from './shared/modules/language-translation/language-translation.module';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AuthGuard } from './shared';

import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { ApplicationsService } from './shared/services/applications.service';
import { EnvironmentsService } from './shared/services/environments.service';
import { ToastService } from './shared/modules/toast/toast.service';
import { TopicsService } from './shared/services/topics.service';

import { ServerInfoService } from './shared/services/serverinfo.service';
import { CertificateService } from './shared/services/certificates.service';
import { SchemaSectionComponent } from './layout/topics/schema-section.component';
import { HIGHLIGHT_OPTIONS } from 'ngx-highlightjs';
import { getHighlightLanguages } from './layout/topics/topics.module';

const keycloakService = new KeycloakService();

@NgModule({
    imports: [
        CommonModule,
        BrowserModule,
        BrowserAnimationsModule,
        HttpClientModule,
        LanguageTranslationModule,
        AppRoutingModule,
        KeycloakAngularModule
    ],
    declarations: [AppComponent],
    providers: [AuthGuard, ApplicationsService, EnvironmentsService, TopicsService, CertificateService, ToastService,
        SchemaSectionComponent,
        ServerInfoService, {
            provide: KeycloakService,
            useValue: keycloakService
        },
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
        // use fetch as it does not require any other services or modules to be loaded
        fetch('/keycloak/config.json', { method: 'GET' })
            .then(resp => resp.json())
            .then(config => this.initKeycloak(config))
            .then(() => app.bootstrap(AppComponent))
            .catch(err => console.error('Could not initialize Keycloak. Application cannot be initialized'));
    }

    private initKeycloak(config: any): Promise<any> {
        return keycloakService.init({
            config: config,
            initOptions: {
                onLoad: 'login-required',
                checkLoginIframe: false
            },
            enableBearerInterceptor: true,
            bearerExcludedUrls: ['/assets']
        });
    }
}
