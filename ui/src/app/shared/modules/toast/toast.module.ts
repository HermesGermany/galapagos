import { NgModule } from '@angular/core';
import { GalapagosToastComponent, GalapagosToastHeaderDirective } from './toast.component';
import { CommonModule } from '@angular/common';

@NgModule({
    declarations: [GalapagosToastComponent, GalapagosToastHeaderDirective],
    imports: [CommonModule],
    exports: [GalapagosToastComponent, GalapagosToastHeaderDirective]
})
export class GalapagosToastModule {
}
