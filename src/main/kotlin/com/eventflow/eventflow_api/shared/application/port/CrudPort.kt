package com.eventflow.eventflow_api.shared.application.port

import java.util.Optional

interface CrudPort<T : Any, ID : Any> {
    fun <S : T> save(entity: S): S
    fun findById(id: ID): Optional<T>
    fun findAll(): List<T>
    fun findAllById(ids: Iterable<ID>): List<T>
    fun deleteById(id: ID)
    fun existsById(id: ID): Boolean
}
