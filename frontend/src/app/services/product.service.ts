import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ImportResult, InventorySummary, PageResponse, ProductDto, SortDirection } from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly baseUrl = '/api/products';

  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, sortBy: string, direction: SortDirection): Observable<PageResponse<ProductDto>> {
    return this.http.get<PageResponse<ProductDto>>(this.baseUrl, {
      params: { page, size, sortBy, direction }
    });
  }

  summary(): Observable<InventorySummary> {
    return this.http.get<InventorySummary>(`${this.baseUrl}/summary`);
  }

  importFile(file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>(`${this.baseUrl}/import`, formData);
  }
}
