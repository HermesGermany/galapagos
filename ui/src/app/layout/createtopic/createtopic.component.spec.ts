import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { KeycloakService } from 'keycloak-angular';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { ServerInfoService, UiConfig } from '../../shared/services/serverinfo.service';
import { CreateTopicComponent } from './createtopic.component';
import { FormsModule } from '@angular/forms';
import { SpinnerWhileModule } from '../../shared/modules/spinner-while/spinner-while.module';
import { TopicsService } from '../../shared/services/topics.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { CertificateService } from '../../shared/services/certificates.service';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('CreateTopicComponent', () => {

    let component: CreateTopicComponent;
    let fixture: ComponentFixture<CreateTopicComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                LanguageTranslationModule,
                HttpClientTestingModule,
                NgbModule,
                FormsModule,
                SpinnerWhileModule,
                NoopAnimationsModule
            ],
            declarations: [CreateTopicComponent],
            providers: [TranslateService,
                KeycloakService,
                ToastService,
                TopicsService,
                CertificateService,
                EnvironmentsService,
                ApplicationsService,
                ServerInfoService
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(CreateTopicComponent);
        component = fixture.componentInstance;
    });

    it('should create CreateTopic Component', () => {
        expect(component).toBeTruthy();
    });

    it('show correct naming convention link', () => {
        const serverInfo = fixture.debugElement.injector.get(ServerInfoService);
        const uiConfig: UiConfig = {
            minDeprecationTime: {
                days: 1, months: 0, years: 0
            },
            customLinks: [
                {
                    id: 'dummy',
                    href: 'https://www.google.com/',
                    label: 'No show',
                    linkType: 'OTHER'
                },
                {
                    id: 'naming-convention',
                    href: 'https://naming-convention.nosuch.domain/',
                    label: 'TEST Naming Convention',
                    linkType: 'EDUCATIONAL'
                }
            ]
        };

        spyOn(serverInfo, 'getUiConfig').and.returnValue(of(uiConfig));
        fixture.detectChanges();

        // there must be an anchor element with given href and text
        const link: HTMLElement = Array.from(fixture.nativeElement.querySelectorAll('a')).find(
            elem => elem['href'] === 'https://naming-convention.nosuch.domain/') as HTMLElement;

        const icon: HTMLElement = fixture.nativeElement.querySelector('.fa-info-circle') as HTMLElement;

        expect(link).toBeTruthy();
        expect(icon).toBeTruthy();
        expect(link.innerHTML.indexOf('TEST Naming Convention') > -1);
    });

    it('Do not show naming convention link, if not configured', () => {
        const serverInfo = fixture.debugElement.injector.get(ServerInfoService);
        const uiConfig: UiConfig = {
            minDeprecationTime: {
                days: 1, months: 0, years: 0
            },
            customLinks: [
                {
                    id: 'dummy',
                    href: 'https://www.google.com/',
                    label: 'No show',
                    linkType: 'OTHER'
                }
            ]
        };

        spyOn(serverInfo, 'getUiConfig').and.returnValue(of(uiConfig));
        fixture.detectChanges();

        // There must NOT be an anchor - we expect even the info icon to not be present
        const icon = fixture.nativeElement.querySelector('.fa-info-circle');
        expect(icon).not.toBeTruthy();
    });

});
