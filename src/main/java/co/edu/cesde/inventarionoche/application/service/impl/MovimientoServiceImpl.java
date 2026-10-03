package co.edu.cesde.inventarionoche.application.service.impl;

import co.edu.cesde.inventarionoche.application.service.MovimientoService;
import co.edu.cesde.inventarionoche.application.service.ProductoService;
import co.edu.cesde.inventarionoche.domain.model.Movimiento;
import co.edu.cesde.inventarionoche.domain.model.Producto;
import co.edu.cesde.inventarionoche.domain.model.TipoMovimiento;
import co.edu.cesde.inventarionoche.infrastructure.exception.InsufficientStockException;
import co.edu.cesde.inventarionoche.infrastructure.exception.ResourceNotFoundException;
import co.edu.cesde.inventarionoche.infrastructure.exception.ValidationException;
import co.edu.cesde.inventarionoche.infrastructure.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoServiceImpl implements MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoService productoService;

    @Override
    @Transactional
    public Movimiento save(Movimiento movimiento) {
        Producto producto = buscarProducto(movimiento);
        aplicarMovimiento(producto, movimiento.getTipo(), movimiento.getCantidad());

        movimiento.setId(null);
        movimiento.setProducto(producto);
        return movimientoRepository.save(movimiento);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Movimiento> findAll() {
        return movimientoRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Movimiento findById(Long id) {
        return movimientoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un movimiento con id " + id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Movimiento movimiento = findById(id);
        revertirMovimiento(movimiento);
        movimientoRepository.delete(movimiento);
    }

    @Override
    @Transactional
    public Movimiento update(Movimiento movimiento) {
        if (movimiento.getId() == null) {
            throw new ValidationException("El id del movimiento es obligatorio para actualizar");
        }

        Movimiento existente = findById(movimiento.getId());

        // se devuelve el stock del movimiento viejo y se aplica el nuevo
        revertirMovimiento(existente);
        Producto producto = buscarProducto(movimiento);
        aplicarMovimiento(producto, movimiento.getTipo(), movimiento.getCantidad());

        existente.setProducto(producto);
        existente.setCantidad(movimiento.getCantidad());
        existente.setTipo(movimiento.getTipo());
        return movimientoRepository.save(existente);
    }

    private Producto buscarProducto(Movimiento movimiento) {
        if (movimiento.getProducto() == null || movimiento.getProducto().getId() == null) {
            throw new ValidationException("El movimiento debe tener el id del producto");
        }
        return productoService.findById(movimiento.getProducto().getId());
    }

    private void aplicarMovimiento(Producto producto, TipoMovimiento tipo, int cantidad) {
        if (tipo == TipoMovimiento.ENTRADA) {
            producto.sumarStock(cantidad);
        } else {
            validarStock(producto, cantidad);
            producto.restarStock(cantidad);
        }
    }

    private void revertirMovimiento(Movimiento movimiento) {
        Producto producto = movimiento.getProducto();
        if (movimiento.getTipo() == TipoMovimiento.ENTRADA) {
            validarStock(producto, movimiento.getCantidad());
            producto.restarStock(movimiento.getCantidad());
        } else {
            producto.sumarStock(movimiento.getCantidad());
        }
    }

    private void validarStock(Producto producto, int cantidad) {
        if (producto.getStock() < cantidad) {
            throw new InsufficientStockException(
                    "Stock insuficiente para el producto '" + producto.getNombre()
                            + "'. Disponible: " + producto.getStock() + ", solicitado: " + cantidad);
        }
    }
}
