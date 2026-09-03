import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { finalize } from 'rxjs';
import { ApiError, InventorySummary, PageResponse, ProductDto, RowError, SortDirection } from '../models/product.model';
import { ProductService } from '../services/product.service';

type ProductSortField = 'productSku' | 'productName' | 'category' | 'purchaseDate' | 'unitPrice' | 'quantity' | 'stockAgeDays';

interface SortableColumn {
  key: ProductSortField;
  label: string;
}

@Component({
  selector: 'app-product-dashboard',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './product-dashboard.component.html',
  styleUrl: './product-dashboard.component.css'
})
export class ProductDashboardComponent implements OnInit {
  columns: SortableColumn[] = [
    { key: 'productSku', label: 'Product SKU' },
    { key: 'productName', label: 'Product Name' },
    { key: 'category', label: 'Category' },
    { key: 'purchaseDate', label: 'Purchase Date' },
    { key: 'unitPrice', label: 'Unit Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'stockAgeDays', label: 'Stock Age (Days)' }
  ];

  products = signal<ProductDto[]>([]);
  summary = signal<InventorySummary | null>(null);
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);
  sortBy = signal<ProductSortField>('productSku');
  direction = signal<SortDirection>('desc');

  loadingTable = signal(false);
  importing = signal(false);

  errorMessage = signal<string | null>(null);
  errorDetails = signal<RowError[]>([]);
  successMessage = signal<string | null>(null);

  constructor(private productService: ProductService) {}

  ngOnInit(): void {
    this.loadSummary();
    this.loadProducts();
  }

  sort(column: ProductSortField): void {
    if (this.sortBy() === column) {
      this.direction.set(this.direction() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortBy.set(column);
      this.direction.set('asc');
    }
    this.page.set(0);
    this.loadProducts();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.page.set(page);
    this.loadProducts();
  }

  changePageSize(event: Event): void {
    const newSize = Number((event.target as HTMLSelectElement).value);
    this.size.set(newSize);
    this.page.set(0);
    this.loadProducts();
  }

  onFileSelected(event: Event, input: HTMLInputElement): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.clearError();
    this.successMessage.set(null);
    if (!/\.xlsx?$/i.test(file.name)) {
      this.errorMessage.set('Please select an Excel file in .xlsx or .xls format.');
      input.value = '';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.errorMessage.set('The selected file is larger than the 10 MB upload limit.');
      input.value = '';
      return;
    }
    this.importing.set(true);

    this.productService.importFile(file).pipe(
      finalize(() => {
        this.importing.set(false);
        input.value = '';
      })
    ).subscribe({
      next: (result) => {
        this.successMessage.set(`${result.importedRows} product${result.importedRows === 1 ? '' : 's'} imported successfully.`);
        this.page.set(0);
        this.loadSummary();
        this.loadProducts();
      },
      error: (response: HttpErrorResponse) => this.handleError(response)
    });
  }

  clearError(): void {
    this.errorMessage.set(null);
    this.errorDetails.set([]);
  }

  private loadProducts(): void {
    this.loadingTable.set(true);
    this.productService.list(this.page(), this.size(), this.sortBy(), this.direction()).pipe(
      finalize(() => this.loadingTable.set(false))
    ).subscribe({
      next: (response: PageResponse<ProductDto>) => {
        this.products.set(response.content);
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
      },
      error: (response: HttpErrorResponse) => this.handleError(response)
    });
  }

  private loadSummary(): void {
    this.productService.summary().subscribe({
      next: (value) => this.summary.set(value),
      error: (response: HttpErrorResponse) => this.handleError(response)
    });
  }

  private handleError(response: HttpErrorResponse): void {
    this.successMessage.set(null);
    const apiError = response.error as Partial<ApiError> | null;
    const fallback = response.status === 0
      ? 'Unable to connect to the inventory service. Please make sure the backend is running.'
      : 'Something went wrong. Please try again.';
    this.errorMessage.set(typeof apiError?.message === 'string' ? apiError.message : fallback);
    this.errorDetails.set(Array.isArray(apiError?.details) ? apiError.details : []);
  }
}
