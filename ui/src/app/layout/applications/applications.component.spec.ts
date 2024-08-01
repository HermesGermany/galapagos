import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TopicsService } from '../../shared/services/topics.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../shared';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApplicationsComponent } from './applications.component';
import { SpinnerWhileModule } from '../../shared/modules/spinner-while/spinner-while.module';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { CertificateService } from '../../shared/services/certificates.service';
import { AuthService } from '../../shared/services/auth.service';
import { MockAuthService } from '../../shared/util/test-util';

describe('ApplicationsComponent', () => {
    let component: ApplicationsComponent;
    let fixture: ComponentFixture<ApplicationsComponent>;

    beforeEach((() => {
        TestBed.configureTestingModule({
            declarations: [ApplicationsComponent],
            imports: [
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                FormsModule,
                CommonModule,
                SpinnerWhileModule
            ],
            providers: [
                { provide: AuthService, useClass: MockAuthService },
                RouterModule,
                TopicsService,
                EnvironmentsService,
                ApplicationsService,
                ApiKeyService,
                CertificateService,
                ServerInfoService,
                ToastService,
                TranslateService,
                NgbModal
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ApplicationsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }));

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
