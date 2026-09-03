import { Component } from '@angular/core';
import { ProductDashboardComponent } from './product-dashboard/product-dashboard.component';

@Component({
  selector: 'app-root',
  imports: [ProductDashboardComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
}
