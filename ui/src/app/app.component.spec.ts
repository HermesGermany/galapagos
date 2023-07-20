import { ComponentFixture, TestBed } from '@angular/core/testing';
import { APP_BASE_HREF } from '@angular/common';

import { AppComponent } from './app.component';
import { AppModule } from './app.module';
import { AuthService } from './shared/services/auth.service';
import { MockAuthService } from './shared/util/test-util';

describe('AppComponent', () => {
    let component: AppComponent;
    let fixture: ComponentFixture<AppComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AppModule],
            providers: [
                { provide: APP_BASE_HREF, useValue: '/' },
                { provide: AuthService, useClass: MockAuthService }
            ]
        }).compileComponents();
    }
    );

    beforeEach(() => {
        fixture = TestBed.createComponent(AppComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
