import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppLinkComponent } from './app-link.component';
import { TranslateModule } from '@ngx-translate/core';
import { NgbHighlight } from '@ng-bootstrap/ng-bootstrap';

@NgModule({
    imports: [CommonModule, TranslateModule, NgbHighlight],
    exports: [
        AppLinkComponent
    ],
    declarations: [AppLinkComponent]
})
export class AppLinkModule {
}
