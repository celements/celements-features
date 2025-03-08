/* initialise Select2 for User Edit Form*/
function initSelect2(select) {
  if (!select) return;
  const jselect = $j(select);
  jselect.select2({
    language: 'de',
    selectionCssClass: "select2UserAdminSelection",
    dropdownCssClass: "select2UserAdminDropdown",
    minimumResultsForSearch: 10,
    width: '100%'
  });
}

document.querySelectorAll('#userEditForm select.selection')
  .forEach(select => initSelect2(select));
