from typing import Any, Callable, List, Optional, Text, Tuple
from types import ModuleType

from .environment import Environment

def split_template_path(template: Text) -> List[Text]: ...

class BaseLoader:
    has_source_access = ...  # type: bool
    def get_source(self, environment, template): ...
    def list_templates(self): ...
    def load(self, environment, name, globals: Optional[Any] = ...): ...

class FileSystemLoader(BaseLoader):
    searchpath = ...  # type: Text
    encoding = ...  # type: Any
    followlinks = ...  # type: Any
    def __init__(self, searchpath: Text, encoding: Text = ..., followlinks: bool = ...) -> None: ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...
    def list_templates(self): ...

class PackageLoader(BaseLoader):
    encoding = ...  # type: Text
    manager = ...  # type: Any
    filesystem_bound = ...  # type: Any
    provider = ...  # type: Any
    package_path = ...  # type: Any
    def __init__(self, package_name: Text, package_path: Text = ..., encoding: Text = ...) -> None: ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...
    def list_templates(self): ...

class DictLoader(BaseLoader):
    mapping = ...  # type: Any
    def __init__(self, mapping) -> None: ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...
    def list_templates(self): ...

class FunctionLoader(BaseLoader):
    load_func = ...  # type: Any
    def __init__(self, load_func) -> None: ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...

class PrefixLoader(BaseLoader):
    mapping = ...  # type: Any
    delimiter = ...  # type: Any
    def __init__(self, mapping, delimiter: str = ...) -> None: ...
    def get_loader(self, template): ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...
    def load(self, environment, name, globals: Optional[Any] = ...): ...
    def list_templates(self): ...

class ChoiceLoader(BaseLoader):
    loaders = ...  # type: Any
    def __init__(self, loaders) -> None: ...
    def get_source(self, environment: Environment, template: Text) -> Tuple[Text, Text, Callable]: ...
    def load(self, environment, name, globals: Optional[Any] = ...): ...
    def list_templates(self): ...

class _TemplateModule(ModuleType): ...

class ModuleLoader(BaseLoader):
    has_source_access = ...  # type: bool
    module = ...  # type: Any
    package_name = ...  # type: Any
    def __init__(self, path) -> None: ...
    @staticmethod
    def get_template_key(name): ...
    @staticmethod
    def get_module_filename(name): ...
    def load(self, environment, name, globals: Optional[Any] = ...): ...
