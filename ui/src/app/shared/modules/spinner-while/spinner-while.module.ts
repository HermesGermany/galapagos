import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { SpinnerWhileDirective } from './spinner-while.directive';

@NgModule({
    imports: [CommonModule],
    declarations: [SpinnerWhileDirective],
    exports: [SpinnerWhileDirective]
})
export class SpinnerWhileModule {

}
