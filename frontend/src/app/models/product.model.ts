export interface ProductDto {
  id: number;
  productSku: string;
  productName: string;
  category: string;
  purchaseDate: string;
  unitPrice: number;
  quantity: number;
  stockAgeDays: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface InventorySummary {
  totalProducts: number;
  totalInventoryValue: number;
  averageStockAgeDays: number;
}

export interface ImportResult {
  importedRows: number;
}

export interface RowError {
  row: number;
  column: string;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  details: RowError[];
}

export type SortDirection = 'asc' | 'desc';
