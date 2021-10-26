import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OpensslCommandComponent } from './openssl-command.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

@NgModule({
    imports: [CommonModule, TranslateModule, FormsModule],
    exports: [
        OpensslCommandComponent
    ],
    declarations: [OpensslCommandComponent]
})
export class OpensslCommandModule {
}
