package lofod.productsapi.controller

import lofod.productsapi.service.*
import org.springframework.web.bind.annotation.*

@RestController
class CatalogWorkspaceController(private val workspace: CatalogWorkspaceService, private val moves: CatalogMoveService) {
    @PostMapping("/catalog/move/prepare")
    fun prepare(@RequestBody request: MoveRequest) = moves.prepare(request)
    @PostMapping("/catalog/move/confirm")
    fun confirm(@RequestBody request: MoveRequest) { moves.confirm(request) }
    @GetMapping("/category/{id}/fields")
    fun fields(@PathVariable id: String) = workspace.fields(id)
    @PostMapping("/category/{id}/fields")
    fun create(@PathVariable id: String, @RequestBody request: FieldChange) = workspace.changeField(id, null, request)
    @PutMapping("/category/{id}/fields/{fieldId}")
    fun update(@PathVariable id: String, @PathVariable fieldId: String, @RequestBody request: FieldChange) =
        workspace.changeField(id, fieldId, request)
    @GetMapping("/catalog/preferences/{pageId}")
    fun preferences(@PathVariable pageId: String) = workspace.preference(pageId)
    @PutMapping("/catalog/preferences/{pageId}")
    fun save(@PathVariable pageId: String, @RequestBody request: CatalogPreference) = workspace.savePreference(pageId, request)
}
